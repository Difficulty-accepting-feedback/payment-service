package com.grow.payment_service.payment.infra.config;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.grow.payment_service.payment.infra.config.QuartzConfig.class)
class QuartzConfigTest {

	@Autowired
	private JobDetail monthlyAutoChargeJobDetail;

	@Autowired
	@Qualifier("monthlyAutoChargeTrigger")
	private Trigger monthlyAutoChargeTrigger;

	@Test
	void jobDetail_shouldHaveInitialRetryCountZero() {
		JobDataMap map = monthlyAutoChargeJobDetail.getJobDataMap();
		assertThat(map.getInt("retryCount"))
			.as("초기 retryCount 는 0 이어야 한다")
			.isZero();
	}

	@Test
	void trigger_shouldUseCorrectCronExpression() {
		String expr = ((CronTrigger) monthlyAutoChargeTrigger).getCronExpression();
		assertThat(expr)
			.as("Trigger 의 cron 식은 '0 0 0 1 * ?' 여야 한다")
			.isEqualTo("0 0 0 1 * ?");
	}
}