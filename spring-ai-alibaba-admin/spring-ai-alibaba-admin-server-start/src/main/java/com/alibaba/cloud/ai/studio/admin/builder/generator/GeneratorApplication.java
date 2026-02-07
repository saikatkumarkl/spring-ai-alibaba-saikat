/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.admin.builder.generator;

import com.alibaba.cloud.ai.studio.admin.builder.controller.SystemController;
import com.alibaba.cloud.ai.studio.admin.builder.generator.config.GraphProjectGenerationConfiguration;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.account.Account;
import com.alibaba.cloud.ai.studio.runtime.domain.account.LoginRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.account.RefreshTokenRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.account.TokenResponse;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstrap class to start the DSL-to-SAA conversion service only
 * (middleware required by studio-admin is optional).
 */
// TODO: Include Studio low-code platform in this bootstrap class.
@SpringBootApplication(exclude = { RedissonAutoConfigurationV2.class, ElasticsearchDataAutoConfiguration.class,
		ElasticsearchRepositoriesAutoConfiguration.class, ElasticsearchRestClientAutoConfiguration.class,
		DataSourceAutoConfiguration.class })
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
		classes = GraphProjectGenerationConfiguration.class))
public class GeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeneratorApplication.class, args);
	}

	// TODO: Let the frontend enter the DSL conversion page without login and remove this MockLoginController.
	@RestController
	@CrossOrigin
	public static class MockLoginController {

		@PostMapping("/console/v1/auth/login")
		public Result<TokenResponse> login(@RequestBody LoginRequest loginRequest) {
			return Result.success(new TokenResponse("token", "refreshToken", 1000L));
		}

		@PostMapping("/console/v1/auth/refresh-token")
		public Result<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
			return Result.success(new TokenResponse("token", "refreshToken", 1000L));
		}

		@GetMapping("/console/v1/system/global-config")
		public Result<SystemController.GlobalConfig> globalConfig() {
			SystemController.GlobalConfig globalConfig = new SystemController.GlobalConfig();
			globalConfig.setLoginMethod("preset_account");
			globalConfig.setUploadMethod("file");
			return Result.success(globalConfig);
		}

		@GetMapping("/console/v1/accounts/profile")
		public Result<Account> getAccountProfile() {
			Account account = new Account();
			account.setUsername("admin");
			return Result.success(account);
		}

	}

}
