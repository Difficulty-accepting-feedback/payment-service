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
import com.grow.payment_service.payment.infra.batch.DailyAutoChargeJob;

@Configuration
public class QuartzConfig {

	private final AutoChargeJobListener autoChargeJobListener;

	public QuartzConfig(AutoChargeJobListener autoChargeJobListener) {
		this.autoChargeJobListener = autoChargeJobListener;
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
			.withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 * * ?"))
			.build();
	}

	/** Quartz Scheduler 설정 */
	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(
		JobDetail dailyAutoChargeJobDetail,
		Trigger dailyAutoChargeTrigger
	) {
		SchedulerFactoryBean factory = new SchedulerFactoryBean();
		factory.setJobDetails(dailyAutoChargeJobDetail);
		factory.setTriggers(dailyAutoChargeTrigger);
		factory.setGlobalJobListeners(autoChargeJobListener);
		return factory;
	}
}