/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample;

import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Application.class)
@TestPropertySource(properties = "server.port=0")
public class SecurityTests {
	@Value("#{@nettyContext.address().getPort()}")
	int port;

	WebTestClient rest;

	@Before
	public void setup() {
		this.rest = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + this.port)
				.build();
	}

	@Test
	public void basicRequired() throws Exception {
		this.rest
			.get()
			.uri("/users")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	public void basicWorks() throws Exception {
		this.rest
			.filter(robsCredentials())
			.get()
			.uri("/users")
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	public void authorizationAdmin401() throws Exception {
		this.rest
			.filter(robsCredentials())
			.get()
			.uri("/admin")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	public void authorizationAdmin200() throws Exception {
		this.rest
			.filter(adminCredentials())
			.get()
			.uri("/admin")
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	public void basicMissingUser401() throws Exception {
		this.rest
			.filter(basicAuthentication("missing-user", "password"))
			.get()
			.uri("/admin")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	public void basicInvalidPassword401() throws Exception {
		this.rest
			.filter(basicAuthentication("rob", "invalid"))
			.get()
			.uri("/admin")
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	public void basicInvalidParts401() throws Exception {
		this.rest
			.get()
			.uri("/admin")
			.header("Authorization", "Basic " + base64Encode("no colon"))
			.exchange()
			.expectStatus().isUnauthorized();
	}

	@Test
	public void sessionWorks() throws Exception {
		ExchangeResult result = this.rest
				.filter(robsCredentials())
				.get()
				.uri("/users")
				.exchange()
				.returnResult();

		String session = result.getResponseHeaders().getFirst("Set-Cookie");

		this.rest
			.get()
			.uri("/users")
			.header("Cookie", session)
			.exchange()
			.expectStatus().isOk();
	}

	@Test
	public void me() throws Exception {
		Map<String,String> expected = Collections.singletonMap("username", "rob");

		this.rest
			.filter(robsCredentials())
			.get()
			.uri("/me")
			.exchange()
			.expectStatus().isOk()
			.expectBody().map(String.class, String.class).isEqualTo(expected);
	}

	@Test
	public void principal() throws Exception {
		Map<String,String> expected = Collections.singletonMap("username", "rob");

		this.rest
				.filter(robsCredentials())
				.get()
				.uri("/principal")
				.exchange()
				.expectStatus().isOk()
				.expectBody().map(String.class, String.class).isEqualTo(expected);
	}

	private ExchangeFilterFunction robsCredentials() {
		return basicAuthentication("rob","rob");
	}

	private ExchangeFilterFunction adminCredentials() {
		return basicAuthentication("admin","admin");
	}

	private String base64Encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(Charset.defaultCharset()));
	}
}
