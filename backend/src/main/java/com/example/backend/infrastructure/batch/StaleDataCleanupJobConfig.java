package com.example.backend.infrastructure.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 오래된 데이터 정리 배치 Job.
 *
 * Step 1 — cleanupQueryLogsStep
 *   : query_logs 중 보존기간(90일) 초과 레코드를 청크(100건) 단위로 삭제
 *
 * Step 2 — cleanupChatSessionsStep
 *   : Step 1 완료 후, 보존기간(30일) 초과이며 남은 query_logs 가 없는
 *     chat_sessions 를 청크 단위로 삭제
 *
 * 청크 기반 처리 이유: 단순 DELETE ... WHERE 는 대용량 테이블에서 장시간 락을 유발.
 * ID 를 먼저 읽어(Reader) 청크 단위로 삭제(Writer)하면 락 점유 시간을 분산할 수 있다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StaleDataCleanupJobConfig {

    private final DataSource dataSource;
    private final BatchMetricsListener metricsListener;

    @Value("${batch.cleanup.query-logs-retention-days:90}")
    private int queryLogsRetentionDays;

    @Value("${batch.cleanup.chat-sessions-retention-days:30}")
    private int chatSessionsRetentionDays;

    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job staleDataCleanupJob(JobRepository jobRepository,
                                   Step cleanupQueryLogsStep,
                                   Step cleanupChatSessionsStep) {
        return new JobBuilder("staleDataCleanupJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(metricsListener)
                .start(cleanupQueryLogsStep)
                .next(cleanupChatSessionsStep)
                .build();
    }

    // ─── Step 1: query_logs 정리 ──────────────────────────────────────────────

    @Bean
    public Step cleanupQueryLogsStep(JobRepository jobRepository,
                                     PlatformTransactionManager txManager) {
        return new StepBuilder("cleanupQueryLogsStep", jobRepository)
                .<Long, Long>chunk(CHUNK_SIZE, txManager)
                .reader(oldQueryLogIdReader())
                .writer(queryLogDeleteWriter())
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .noRetry(DataIntegrityViolationException.class)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<Long> oldQueryLogIdReader() {
        String sql = "SELECT id FROM query_logs" +
                     " WHERE created_at < NOW() - INTERVAL '" + queryLogsRetentionDays + " days'" +
                     " ORDER BY id";
        return new JdbcCursorItemReaderBuilder<Long>()
                .name("oldQueryLogIdReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper((rs, rowNum) -> rs.getLong("id"))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Long> queryLogDeleteWriter() {
        return new JdbcBatchItemWriterBuilder<Long>()
                .dataSource(dataSource)
                .sql("DELETE FROM query_logs WHERE id = ?")
                .itemPreparedStatementSetter((id, ps) -> ps.setLong(1, id))
                .assertUpdates(false)
                .build();
    }

    // ─── Step 2: chat_sessions 정리 ──────────────────────────────────────────

    @Bean
    public Step cleanupChatSessionsStep(JobRepository jobRepository,
                                        PlatformTransactionManager txManager) {
        return new StepBuilder("cleanupChatSessionsStep", jobRepository)
                .<Long, Long>chunk(CHUNK_SIZE, txManager)
                .reader(inactiveChatSessionIdReader())
                .writer(chatSessionDeleteWriter())
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .noRetry(DataIntegrityViolationException.class)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<Long> inactiveChatSessionIdReader() {
        // Step 1에서 오래된 query_logs 를 삭제한 뒤 실행되므로,
        // 남은 query_logs 가 없는 세션 = 진짜 비활성 세션
        String sql = "SELECT cs.id FROM chat_sessions cs" +
                     " WHERE cs.created_at < NOW() - INTERVAL '" + chatSessionsRetentionDays + " days'" +
                     " AND NOT EXISTS (" +
                     "   SELECT 1 FROM query_logs ql WHERE ql.session_id = cs.id" +
                     " ) ORDER BY cs.id";
        return new JdbcCursorItemReaderBuilder<Long>()
                .name("inactiveChatSessionIdReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper((rs, rowNum) -> rs.getLong("id"))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Long> chatSessionDeleteWriter() {
        return new JdbcBatchItemWriterBuilder<Long>()
                .dataSource(dataSource)
                .sql("DELETE FROM chat_sessions WHERE id = ?")
                .itemPreparedStatementSetter((id, ps) -> ps.setLong(1, id))
                .assertUpdates(false)
                .build();
    }
}
