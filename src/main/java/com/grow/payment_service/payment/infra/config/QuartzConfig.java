package com.grow.payment_service.payment.infra.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.grow.payment_service.payment.infra.scheduler.MonthlyAutoChargeJob;

@Configuration
public class QuartzConfig {

	@Bean
	public JobDetail monthlyAutoChargeJobDetail() {
		return JobBuilder.newJob(MonthlyAutoChargeJob.class)
				.withIdentity("monthlyAutoChargeJob")
				.storeDurably()
				.build();
	}

	@Bean
	public Trigger monthlyAutoChargeTrigger(JobDetail monthlyAutoChargeJobDetail) {
		return TriggerBuilder.newTrigger()
				.forJob(monthlyAutoChargeJobDetail)
				.withIdentity("monthlyAutoChargeTrigger")
			.withSchedule(CronScheduleBuilder
				.cronSchedule("0 0 0 1 * ?")
				.build();
	}
}