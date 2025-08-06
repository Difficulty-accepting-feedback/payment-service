package com.grow.payment_service.global.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.grow.payment_service.payment.infra.batch.AutoChargeJobListener;
import com.grow.payment_service.payment.infra.batch.DailyAutoChargeJob;
import com.grow.payment_service.subscription.infra.batch.SubscriptionExpiryJob;
import com.grow.payment_service.subscription.infra.batch.SubscriptionExpiryJobListener;

@Configuration
public class QuartzConfig {

	private final AutoChargeJobListener autoChargeJobListener;
	private final SubscriptionExpiryJobListener subscriptionExpiryJobListener;

	public QuartzConfig(
		AutoChargeJobListener autoChargeJobListener,
		SubscriptionExpiryJobListener subscriptionExpiryJobListener
	) {
		this.autoChargeJobListener = autoChargeJobListener;
		this.subscriptionExpiryJobListener = subscriptionExpiryJobListener;
	}

	/** DailyAutoChargeJob Bean 등록 */
	@Bean
	public JobDetail dailyAutoChargeJobDetail() {
		return JobBuilder.newJob(DailyAutoChargeJob.class)
			.withIdentity("dailyAutoChargeJob")
			.storeDurably()
			.build();
	}

	/** 매일 0시 DailyAutoChargeJob Trigger */
	@Bean
	public Trigger dailyAutoChargeTrigger(JobDetail dailyAutoChargeJobDetail) {
		return TriggerBuilder.newTrigger()
			.forJob(dailyAutoChargeJobDetail)
			.withIdentity("dailyAutoChargeTrigger")
			.withSchedule(
				CronScheduleBuilder.cronSchedule("0 0 0 * * ?")
					.withMisfireHandlingInstructionFireAndProceed()
			)
			.build();
	}

	/** 구독 만료 JobDetail Bean */
	@Bean
	public JobDetail subscriptionExpiryJobDetail() {
		return JobBuilder.newJob(SubscriptionExpiryJob.class)
			.withIdentity("subscriptionExpiryJob")
			.storeDurably()
			.usingJobData("retryCount", 0)
			.usingJobData("maxRetry", 3)
			.build();
	}

	/** 매일 0시 구독만료 실행 Trigger */
	@Bean
	public Trigger subscriptionExpiryTrigger(JobDetail subscriptionExpiryJobDetail) {
		return TriggerBuilder.newTrigger()
			.forJob(subscriptionExpiryJobDetail)
			.withIdentity("subscriptionExpiryTrigger")
			.withSchedule(
				CronScheduleBuilder.cronSchedule("0 0 0 * * ?")
					.withMisfireHandlingInstructionFireAndProceed()
			)
			.build();
	}

	/** Quartz Scheduler 설정: JobDetail, Trigger, Global Listener 등록 */
	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(
		JobDetail dailyAutoChargeJobDetail,
		Trigger dailyAutoChargeTrigger,
		JobDetail subscriptionExpiryJobDetail,
		Trigger subscriptionExpiryTrigger
	) {
		SchedulerFactoryBean factory = new SchedulerFactoryBean();
		factory.setJobDetails(dailyAutoChargeJobDetail, subscriptionExpiryJobDetail);
		factory.setTriggers(dailyAutoChargeTrigger, subscriptionExpiryTrigger);
		factory.setGlobalJobListeners(autoChargeJobListener, subscriptionExpiryJobListener);
		return factory;
	}
}