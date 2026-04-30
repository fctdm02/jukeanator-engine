package com.djt.jukeanator_engine;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.testcontainers.DockerClientFactory;

public class DockerAvailableCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Exception e) {
			return false;
		}
	}
}