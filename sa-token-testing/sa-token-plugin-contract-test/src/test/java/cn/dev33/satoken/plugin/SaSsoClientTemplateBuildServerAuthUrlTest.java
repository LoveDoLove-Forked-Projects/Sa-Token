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

import cn.dev33.satoken.sso.SaSsoManager;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaFoxUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SSO Client {@code buildServerAuthUrl}：避免重复追加 back 参数
 */
public class SaSsoClientTemplateBuildServerAuthUrlTest {

	private SaSsoClientConfig backupClientConfig;
	private SaSsoClientTemplate template;

	/** 每个用例开始前准备测试现场 */
	@BeforeEach
	public void setup() {
		backupClientConfig = SaSsoManager.getClientConfig();
		SaSsoManager.setClientConfig(new SaSsoClientConfig().setServerUrl("http://sso-server.com"));
		template = new SaSsoClientTemplate();
	}

	/** 把全局状态恢复回去 */
	@AfterEach
	public void restore() {
		SaSsoManager.setClientConfig(backupClientConfig);
	}

	/** clientLoginUrl 没有 back 参数时应该补上 */
	@Test
	public void appendBack_whenClientLoginUrlHasNoBack() {
		String url = template.buildServerAuthUrl("http://client.com/sso/login", "http://client.com/index");
		Assertions.assertTrue(url.contains("?back=" + SaFoxUtil.encodeUrl("http://client.com/index")));
	}

	/** URL 里已经有 ?back= 时不应该再补一份 */
	@Test
	public void skipAppend_whenPlainQuestionBackAlreadyPresent() {
		String clientLoginUrl = "http://client.com/sso/login?back=http://client.com/index";
		String url = template.buildServerAuthUrl(clientLoginUrl, "http://client.com/other");
		Assertions.assertTrue(url.contains(clientLoginUrl));
		Assertions.assertFalse(url.contains("&back="));
	}

	/** URL 里已经有 &back= 时不应该再补一份 */
	@Test
	public void skipAppend_whenPlainAmpersandBackAlreadyPresent() {
		String clientLoginUrl = "http://client.com/sso/login?foo=1&back=http://client.com/index";
		String url = template.buildServerAuthUrl(clientLoginUrl, "http://client.com/other");
		Assertions.assertTrue(url.contains(clientLoginUrl));
		Assertions.assertEquals(1, countIgnoreCase(url, "&back="));
	}

	/** 参数名只是长得像 back 时还是应该补上真正的 back */
	@Test
	public void stillAppend_whenParamNameOnlyLooksLikeBack() {
		String url = template.buildServerAuthUrl("http://client.com/sso/login?abcback=1", "http://client.com/index");
		Assertions.assertTrue(url.contains("&back=" + SaFoxUtil.encodeUrl("http://client.com/index")));
	}

	/** back 为空时不应该往 URL 上拼 */
	@Test
	public void skipAppend_whenBackIsEmpty() {
		String clientLoginUrl = "http://client.com/sso/login";
		String url = template.buildServerAuthUrl(clientLoginUrl, "");
		Assertions.assertFalse(url.contains("back="));
		Assertions.assertTrue(url.endsWith("redirect=" + clientLoginUrl));
	}

	private static int countIgnoreCase(String text, String needle) {
		String lower = text.toLowerCase();
		String n = needle.toLowerCase();
		int count = 0;
		int from = 0;
		while ((from = lower.indexOf(n, from)) != -1) {
			count++;
			from += n.length();
		}
		return count;
	}

}
