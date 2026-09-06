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
package cn.dev33.satoken.dao;

import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SaTokenDaoForRedisTemplate}#searchData 测试
 *
 * @author click33
 * @since 1.46.0
 */
public class SaTokenDaoForRedisTemplateSearchDataTest {

	private static final String PREFIX = "satoken:login:token:";

	private static RedisServer redisServer;

	private LettuceConnectionFactory connectionFactory;

	private SaTokenDaoForRedisTemplate dao;

	/** 启动无密码的内嵌 Redis，端口随机 */
	@BeforeAll
	static void startRedis() throws IOException {
		redisServer = JedisMockRedisSupport.startServer();
	}

	/** 测完把内嵌 Redis 关掉 */
	@AfterAll
	static void stopRedis() throws IOException {
		JedisMockRedisSupport.stopServer(redisServer);
	}

	/** 每个用例开始前准备测试现场 */
	@BeforeEach
	void setUp() {
		connectionFactory = JedisMockRedisSupport.createFactory(redisServer);
		dao = new SaTokenDaoForRedisTemplate();
		dao.init(connectionFactory);
		JedisMockRedisSupport.flushDb(connectionFactory);
	}

	/** 每个用例结束后把测试现场清掉 */
	@AfterEach
	void tearDown() {
		JedisMockRedisSupport.destroyFactory(connectionFactory);
	}

	/** 按前缀搜索时应该只返回该前缀下的 key */
	@Test
	void searchData_shouldReturnKeysUnderPrefix() {
		dao.set(PREFIX + "token-a", "1", 60);
		dao.set(PREFIX + "token-b", "1", 60);
		dao.set("satoken:login:session:s1", "1", 60);

		List<String> list = dao.searchData(PREFIX, "", 0, -1, true);

		Assertions.assertEquals(2, list.size());
		Assertions.assertTrue(list.contains(PREFIX + "token-a"));
		Assertions.assertTrue(list.contains(PREFIX + "token-b"));
	}

	/** 带 keyword 时应该只留下命中关键字的 key */
	@Test
	void searchData_shouldFilterByKeyword() {
		dao.set(PREFIX + "user-10001", "1", 60);
		dao.set(PREFIX + "user-10002", "1", 60);
		dao.set(PREFIX + "guest-10001", "1", 60);

		List<String> list = dao.searchData(PREFIX, "10001", 0, -1, true);

		Assertions.assertEquals(2, list.size());
		Assertions.assertTrue(list.contains(PREFIX + "user-10001"));
		Assertions.assertTrue(list.contains(PREFIX + "guest-10001"));
	}

	/** 分页参数应该能从完整结果里切出对应窗口 */
	@Test
	void searchData_shouldSupportPagination() {
		dao.set(PREFIX + "k-01", "1", 60);
		dao.set(PREFIX + "k-02", "1", 60);
		dao.set(PREFIX + "k-03", "1", 60);
		dao.set(PREFIX + "k-04", "1", 60);
		dao.set(PREFIX + "k-05", "1", 60);

		List<String> all = dao.searchData(PREFIX, "", 0, -1, true);
		List<String> page = dao.searchData(PREFIX, "", 1, 2, true);

		Assertions.assertEquals(2, page.size());
		Assertions.assertEquals(all.get(1), page.get(0));
		Assertions.assertEquals(all.get(2), page.get(1));
	}

	/** sortType=false 时应该把结果倒过来 */
	@Test
	void searchData_shouldSupportReverseSort() {
		dao.set(PREFIX + "k-01", "1", 60);
		dao.set(PREFIX + "k-02", "1", 60);
		dao.set(PREFIX + "k-03", "1", 60);

		List<String> asc = dao.searchData(PREFIX, "", 0, -1, true);
		List<String> desc = dao.searchData(PREFIX, "", 0, -1, false);

		Assertions.assertEquals(asc.size(), desc.size());
		Assertions.assertEquals(asc.get(0), desc.get(desc.size() - 1));
		Assertions.assertEquals(asc.get(asc.size() - 1), desc.get(0));
	}

	/** 关键字一个都对不上时应该返回空列表 */
	@Test
	void searchData_shouldReturnEmptyWhenNoMatch() {
		dao.set(PREFIX + "only-one", "1", 60);

		List<String> list = dao.searchData(PREFIX, "not-exist-keyword", 0, -1, true);

		Assertions.assertTrue(list.isEmpty());
	}

	/** Redis 里没有数据时搜索应该返回空列表 */
	@Test
	void searchData_shouldReturnEmptyWhenRedisIsEmpty() {
		List<String> list = dao.searchData(PREFIX, "", 0, -1, true);
		Assertions.assertTrue(list.isEmpty());
	}

	/** wrapKey 应该作用在完整 pattern 上，搜出来的 key 也带前缀 */
	@Test
	void searchData_shouldRespectWrapKeyOnFullPattern() {
		SaTokenDaoForRedisTemplate prefixedDao = new SaTokenDaoForRedisTemplate() {
			@Override
			public String wrapKey(String key) {
				return "app:" + key;
			}
		};
		prefixedDao.init(connectionFactory);

		prefixedDao.set(PREFIX + "wrapped-a", "1", 60);
		prefixedDao.set(PREFIX + "wrapped-b", "1", 60);
		prefixedDao.set("other:key", "1", 60);

		List<String> list = prefixedDao.searchData(PREFIX, "wrapped", 0, -1, true);

		Assertions.assertEquals(2, list.size());
		for (String key : list) {
			Assertions.assertTrue(key.startsWith("app:" + PREFIX));
		}
	}

	/** 前缀对不上的 key 即使关键字相同也不应该被搜到 */
	@Test
	void searchData_shouldNotMatchKeysOutsidePrefixPattern() {
		dao.set("satoken:other:token:abc", "1", 60);
		dao.set(PREFIX + "abc", "1", 60);

		List<String> list = dao.searchData(PREFIX, "abc", 0, -1, true);

		Assertions.assertEquals(1, list.size());
		Assertions.assertEquals(PREFIX + "abc", list.get(0));
	}

	/** key 比较多时走 SCAN 也应该能搜全 */
	@Test
	void searchData_shouldHandleManyKeysViaScan() {
		for (int i = 0; i < 50; i++) {
			dao.set(PREFIX + "bulk-" + String.format("%02d", i), "1", 60);
		}
		dao.set(PREFIX + "bulk-noise", "1", 60);

		List<String> list = dao.searchData(PREFIX, "bulk-", 0, -1, true);

		Assertions.assertEquals(51, list.size());
	}

	/** start 已经超过结果长度时应该返回空列表 */
	@Test
	void searchData_shouldReturnPartialPageWhenStartBeyondEnd() {
		dao.set(PREFIX + "only", "1", 60);

		List<String> list = dao.searchData(PREFIX, "", 10, 5, true);

		Assertions.assertTrue(list.isEmpty());
	}

	/** SCAN 扫到重复 key 时，结果里应该只留一份 */
	@Test
	void searchData_shouldDeduplicateScanResults() {
		String duplicateKey = PREFIX + "dup-key";
		byte[] keyBytes = duplicateKey.getBytes(StandardCharsets.UTF_8);

		Cursor<byte[]> cursor = mock(Cursor.class);
		when(cursor.hasNext()).thenReturn(true, true, true, false);
		when(cursor.next()).thenReturn(keyBytes, keyBytes, keyBytes);

		RedisConnection connection = mock(RedisConnection.class);
		when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

		RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
		when(mockFactory.getConnection()).thenReturn(connection);

		SaTokenDaoForRedisTemplate mockDao = new SaTokenDaoForRedisTemplate();
		mockDao.stringRedisTemplate = new StringRedisTemplate(mockFactory);
		mockDao.stringRedisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
		mockDao.stringRedisTemplate.setValueSerializer(StringRedisSerializer.UTF_8);
		mockDao.stringRedisTemplate.afterPropertiesSet();

		List<String> list = mockDao.searchData(PREFIX, "dup", 0, -1, true);

		Assertions.assertEquals(1, list.size());
		Assertions.assertEquals(duplicateKey, list.get(0));
	}

}
