/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.springlets.mail.config;

import org.springframework.util.Assert;

/**
 * Delegating implementation of {@link SpringletsMailConfigurer} that will forward
 * all calls to configuration methods to all registered {@link SpringletsMailConfigurer}.
 *
 * Based on https://github.com/spring-projects/spring-data-rest[Spring Data REST] project.
 *
 * @author Manuel Iborra at http://www.disid.com[DISID Corporation S.L.]
 */
class SpringletsMailConfigurerDelegate implements SpringletsMailConfigurer {

	private final Iterable<SpringletsMailConfigurer> delegates;

	/**
	 * Creates a new {@link SpringletsMailConfigurerDelegate} for the given
	 * {@link SpringletsMailConfigurer}s.
	 *
	 * @param delegates must not be {@literal null}.
	 */
	public SpringletsMailConfigurerDelegate(Iterable<SpringletsMailConfigurer> delegates) {

		Assert.notNull(delegates, "SpringletsMailConfigurers must not be null!");

		this.delegates = delegates;
	}

	@Override
	public void configureSpringletsMailSettings(SpringletsMailSettings config) {
		for (SpringletsMailConfigurer configurer : delegates) {
			configurer.configureSpringletsMailSettings(config);
		}
	}

}
