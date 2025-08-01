package com.grow.payment_service.payment.infra.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.grow.payment_service.payment.infra.batch.AutoChargeJobListener;
import com.grow.payment_service.payment.infra.batch.MonthlyAutoChargeJob;

@Configuration
public class QuartzConfig {

	private final AutoChargeJobListener autoChargeJobListener;

	public QuartzConfig(AutoChargeJobListener autoChargeJobListener) {
		this.autoChargeJobListener = autoChargeJobListener;
	}

	/**
	 * 매월 1일 0시에 실행되는 자동 결제 작업의 JobDetail을 생성합니다.
	 */
	@Bean
	public JobDetail monthlyAutoChargeJobDetail() {
		return JobBuilder.newJob(MonthlyAutoChargeJob.class)
			.withIdentity("monthlyAutoChargeJob")
			.storeDurably()
			.usingJobData("retryCount", 0)
			.usingJobData("maxRetry", 5)
			.build();
	}

	/**
	 * 매월 1일 0시에 자동 결제 작업을 트리거합니다.
	 */
	@Bean
	public Trigger monthlyAutoChargeTrigger(JobDetail monthlyAutoChargeJobDetail) {
		return TriggerBuilder.newTrigger()
			.forJob(monthlyAutoChargeJobDetail)
			.withIdentity("monthlyAutoChargeTrigger")
			.withSchedule(CronScheduleBuilder
				.cronSchedule("0 0 0 1 * ?")
			)
			.build();
	}

	/**
	 * Quartz 스케줄러 팩토리 빈을 생성합니다.
	 */
	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(
		JobDetail monthlyAutoChargeJobDetail,
		Trigger monthlyAutoChargeTrigger
	) {
		SchedulerFactoryBean factory = new SchedulerFactoryBean();
		factory.setJobDetails(monthlyAutoChargeJobDetail);
		factory.setTriggers(monthlyAutoChargeTrigger);
		factory.setGlobalJobListeners(autoChargeJobListener);
		return factory;
	}
}