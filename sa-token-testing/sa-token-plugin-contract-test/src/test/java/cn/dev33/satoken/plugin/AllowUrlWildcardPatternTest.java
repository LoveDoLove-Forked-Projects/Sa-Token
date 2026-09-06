/*
 * Copyright 2020-2099 sa-token.com
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
package cn.dev33.satoken.plugin;

import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.config.SaOAuth2ServerConfig;
import cn.dev33.satoken.oauth2.data.model.loader.SaClientModel;
import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientModel;
import cn.dev33.satoken.sso.config.SaSsoServerConfig;
import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.util.SaFoxUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * SSO / OAuth2 allow-url 通配符配置校验
 */
public class AllowUrlWildcardPatternTest {

	private SaSsoServerConfig backupSsoServerConfig;
	private SaOAuth2ServerConfig backupOAuth2ServerConfig;

	/** 先把 Manager 全局状态存一份 */
	@BeforeEach
	public void backupManagers() {
		backupSsoServerConfig = SaSsoManager.getServerConfig();
		backupOAuth2ServerConfig = SaOAuth2Manager.getServerConfig();
	}

	/** 把 Manager 全局状态恢复回去 */
	@AfterEach
	public void restoreManagers() {
		SaSsoManager.setServerConfig(backupSsoServerConfig);
		SaOAuth2Manager.setServerConfig(backupOAuth2ServerConfig);
	}

	/** OAuth2 allowUrl 带通配符时应该能匹配对应回调地址 */
	@Test
	public void oauth2AllowUrlWildcardPattern() {
		Assertions.assertDoesNotThrow(() ->
				SaOAuth2Template.checkRedirectUriListNormalStaticMethod(
						Arrays.asList("http://sa-sso-client1.com/sso/login", "http://sa-sso-client1.com/*", "http://sa-sso-client1.com:9003/*", "http://sa-sso-client1.com:*")));
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				SaOAuth2Template.checkRedirectUriListNormalStaticMethod(
						Collections.singletonList("http://sa-sso-client1.com*")));
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				SaOAuth2Template.checkRedirectUriListNormalStaticMethod(
						Collections.singletonList("http://sa-sso-client1.com:9003*")));
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				SaOAuth2Template.checkRedirectUriListNormalStaticMethod(
						Collections.singletonList("http://*.sa-sso-client1.com/")));
	}

	/** SSO allowUrl 带通配符时应该能匹配对应回调地址 */
	@Test
	public void ssoAllowUrlWildcardPattern() {
		Assertions.assertDoesNotThrow(() ->
				SaSsoServerTemplate.checkAllowUrlListStaticMethod(
						Arrays.asList("*", "http://sa-sso-client1.com:9003/*", "http://sa-sso-client1.com:*")));
		Assertions.assertThrows(SaSsoException.class, () ->
				SaSsoServerTemplate.checkAllowUrlListStaticMethod(
						Collections.singletonList("http://sa-sso-client1.com*")));
		Assertions.assertThrows(SaSsoException.class, () ->
				SaSsoServerTemplate.checkAllowUrlListStaticMethod(
						Collections.singletonList("http://sa-sso-client1.com:9003*")));
	}

	/** OAuth2 回调地址带 @ 时必须拒绝 */
	@Test
	public void oauth2RejectAtInRedirectUri() {
		SaOAuth2Template template = new SaOAuth2Template();
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("1001", "http://sa-oauth-client.com:123@sa-token.com"));
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("1001", "http://sa-oauth-client.com:123%40sa-token.com"));
		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("1001", "http://sa-oauth-client.com:123%2540sa-token.com"));
	}

	/** SSO 回调地址带 @ 时必须拒绝 */
	@Test
	public void ssoRejectAtInRedirectUrl() {
		SaSsoServerTemplate template = new SaSsoServerTemplate();
		Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("sso-client1", "http://sa-sso-client1.com:9003@sa-token.com"));
		Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("sso-client1", "http://sa-sso-client1.com:9003%40sa-token.com"));
		Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("sso-client1", "http://sa-sso-client1.com:9003%2540sa-token.com"));
	}

	/** allowUrl 配 IPv6 字面量时应该能配进去 */
	@Test
	public void redirectUrlIpv6AllowUrlConfig() {
		Assertions.assertDoesNotThrow(() ->
				SaSsoServerTemplate.checkAllowUrlListStaticMethod(
						Arrays.asList("http://[::1]:9003/*", "http://[2001:db8::1]:8080/*")));
		Assertions.assertDoesNotThrow(() ->
				SaOAuth2Template.checkRedirectUriListNormalStaticMethod(
						Collections.singletonList("http://[::1]:9003/*")));
		Assertions.assertTrue(SaFoxUtil.vagueMatch("http://[::1]:9003/*", "http://[::1]:9003/sso/login"));
		Assertions.assertTrue(SaFoxUtil.vagueMatch("http://[2001:db8::1]:8080/*", "http://[2001:db8::1]:8080/callback"));
	}

	/** SSO 校验带中括号的 IPv6 回调地址时应该通过 */
	@Test
	public void ssoCheckRedirectUrl_ipv6_success() {
		SaSsoClientModel client = new SaSsoClientModel()
				.setClient("ipv6-client")
				.setAllowUrl("http://[::1]:9003/*,http://[2001:db8::1]:8080/*");
		Map<String, SaSsoClientModel> clients = new HashMap<>();
		clients.put("ipv6-client", client);
		SaSsoManager.setServerConfig(new SaSsoServerConfig().setClients(clients));

		SaSsoServerTemplate template = new SaSsoServerTemplate();
		Assertions.assertDoesNotThrow(() ->
				template.checkRedirectUrl("ipv6-client", "http://[::1]:9003/sso/login"));
		Assertions.assertDoesNotThrow(() ->
				template.checkRedirectUrl("ipv6-client", "http://[2001:db8::1]:8080/callback?back=/"));
	}

	/** SSO 校验无中括号或非法 IPv6 回调地址时必须拒绝 */
	@Test
	public void ssoCheckRedirectUrl_ipv6_rejectUnbracketedAndIllegal() {
		SaSsoClientModel client = new SaSsoClientModel()
				.setClient("ipv6-client")
				.setAllowUrl("http://[::1]:9003/*");
		Map<String, SaSsoClientModel> clients = new HashMap<>();
		clients.put("ipv6-client", client);
		SaSsoManager.setServerConfig(new SaSsoServerConfig().setClients(clients));

		SaSsoServerTemplate template = new SaSsoServerTemplate();
		SaSsoException ex1 = Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("ipv6-client", "http://::1:9003/sso/login"));
		Assertions.assertTrue(ex1.getMessage().contains("无效redirect"));

		SaSsoException ex2 = Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("ipv6-client", "http://[::2]:9003/sso/login"));
		Assertions.assertTrue(ex2.getMessage().contains("非法redirect"));

		Assertions.assertThrows(SaSsoException.class, () ->
				template.checkRedirectUrl("ipv6-client", "http://[::1]:9003@evil.com/sso/login"));
	}

	/** OAuth2 校验带中括号的 IPv6 回调地址时应该通过 */
	@Test
	public void oauth2CheckRedirectUri_ipv6_success() {
		SaClientModel client = new SaClientModel()
				.setClientId("ipv6-oauth2")
				.addAllowRedirectUris("http://[::1]:9003/*", "http://[2001:db8::1]:8080/callback");
		Map<String, SaClientModel> clients = new HashMap<>();
		clients.put("ipv6-oauth2", client);
		SaOAuth2Manager.setServerConfig(new SaOAuth2ServerConfig().setClients(clients));

		SaOAuth2Template template = new SaOAuth2Template();
		Assertions.assertDoesNotThrow(() ->
				template.checkRedirectUri("ipv6-oauth2", "http://[::1]:9003/sso/login"));
		Assertions.assertDoesNotThrow(() ->
				template.checkRedirectUri("ipv6-oauth2", "http://[2001:db8::1]:8080/callback"));
	}

	/** OAuth2 校验无中括号或非法 IPv6 回调地址时必须拒绝 */
	@Test
	public void oauth2CheckRedirectUri_ipv6_rejectUnbracketedAndIllegal() {
		SaClientModel client = new SaClientModel()
				.setClientId("ipv6-oauth2")
				.addAllowRedirectUris("http://[::1]:9003/*");
		Map<String, SaClientModel> clients = new HashMap<>();
		clients.put("ipv6-oauth2", client);
		SaOAuth2Manager.setServerConfig(new SaOAuth2ServerConfig().setClients(clients));

		SaOAuth2Template template = new SaOAuth2Template();
		SaOAuth2Exception ex1 = Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("ipv6-oauth2", "http://2001:db8::1:8080/callback"));
		Assertions.assertTrue(ex1.getMessage().contains("无效 redirect_url"));

		SaOAuth2Exception ex2 = Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("ipv6-oauth2", "http://[::2]:9003/sso/login"));
		Assertions.assertTrue(ex2.getMessage().contains("非法 redirect_url"));

		Assertions.assertThrows(SaOAuth2Exception.class, () ->
				template.checkRedirectUri("ipv6-oauth2", "http://[::1]:9003%40evil.com/sso/login"));
	}

}
