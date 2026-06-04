package com.example.backend.infrastructure.batch;

import lombok.RequiredArgsConstructor;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 일별 쿼리 통계 집계 배치 Job.
 *
 * 전날(CURRENT_DATE - 1)의 query_logs 를 user_id 별로 집계해
 * query_stats 테이블에 UPSERT 한다.
 *
 * 멱등성 보장: 동일 날짜 재실행 시 ON CONFLICT DO UPDATE 로 덮어씀.
 * 따라서 집계 오류 발생 후 재실행해도 중복 레코드가 생기지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class DailyStatsJobConfig {

    private final DataSource dataSource;
    private final BatchMetricsListener metricsListener;

    private static final int CHUNK_SIZE = 500;

    @Bean
    public Job dailyStatsJob(JobRepository jobRepository, Step aggregateStatsStep) {
        return new JobBuilder("dailyStatsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(metricsListener)
                .start(aggregateStatsStep)
                .build();
    }

    @Bean
    public Step aggregateStatsStep(JobRepository jobRepository,
                                   PlatformTransactionManager txManager) {
        return new StepBuilder("aggregateStatsStep", jobRepository)
                .<long[], long[]>chunk(CHUNK_SIZE, txManager)
                .reader(queryLogStatsReader())
                .writer(queryStatsUpsertWriter())
                .build();
    }

    @Bean
    public JdbcCursorItemReader<long[]> queryLogStatsReader() {
        // long[0] = user_id, long[1] = query_count
        return new JdbcCursorItemReaderBuilder<long[]>()
                .name("queryLogStatsReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT user_id, COUNT(*) AS query_count
                        FROM query_logs
                        WHERE created_at::date = CURRENT_DATE - INTERVAL '1 day'
                        GROUP BY user_id
                        ORDER BY user_id
                        """)
                .rowMapper((rs, rowNum) -> new long[]{
                        rs.getLong("user_id"),
                        rs.getLong("query_count")
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<long[]> queryStatsUpsertWriter() {
        return new JdbcBatchItemWriterBuilder<long[]>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO query_stats (stat_date, user_id, query_count, created_at, updated_at)
                        VALUES (CURRENT_DATE - 1, ?, ?, NOW(), NOW())
                        ON CONFLICT (stat_date, user_id)
                        DO UPDATE SET query_count = EXCLUDED.query_count, updated_at = NOW()
                        """)
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setLong(1, item[0]);
                    ps.setLong(2, item[1]);
                })
                .build();
    }
}
