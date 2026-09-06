/*
 * Copyright 2020-2099 sa-token.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License at
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
import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

/**
 * {@link SaTokenDaoForRedisTemplate} 字符串读写、超时与初始化测试
 *
 * @author click33
 * @since 1.46.0
 */
public class SaTokenDaoForRedisTemplateTest {

	private static RedisServer redisServer;

	private LettuceConnectionFactory connectionFactory;

	private SaTokenDaoForRedisTemplate dao;

	@BeforeAll
	static void startRedis() throws IOException {
		redisServer = RedisServer.newRedisServer();
		redisServer.start();
	}

	@AfterAll
	static void stopRedis() throws IOException {
		if (redisServer != null) {
			redisServer.stop();
		}
	}

	@BeforeEach
	void setUp() {
		connectionFactory = createConnectionFactory();
		dao = new SaTokenDaoForRedisTemplate();
		dao.init(connectionFactory);
		flushDb();
	}

	@AfterEach
	void tearDown() {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	/** 连上内嵌 Redis 后，set/get 应该能正常读写 */
	@Test
	void get_shouldReturnValueAfterSet() {
		dao.set("name", "zhangsan", 60);
		Assertions.assertEquals("zhangsan", dao.get("name"));
	}

	/** 不存在的 key 取值时应该返回 null */
	@Test
	void get_shouldReturnNullWhenMissing() {
		Assertions.assertNull(dao.get("missing"));
	}

	/** timeout=0 时应该不写入 */
	@Test
	void set_shouldIgnoreZeroTimeout() {
		dao.set("avatar", "1.jpg", 0);
		Assertions.assertNull(dao.get("avatar"));
	}

	/** timeout=-2 时应该不写入 */
	@Test
	void set_shouldIgnoreNotValueExpireTimeout() {
		dao.set("avatar", "1.jpg", SaTokenDao.NOT_VALUE_EXPIRE);
		Assertions.assertNull(dao.get("avatar"));
	}

	/** timeout 比 -2 更小时也应该不写入 */
	@Test
	void set_shouldIgnoreTimeoutLessThanNotValueExpire() {
		dao.set("avatar", "1.jpg", -9);
		Assertions.assertNull(dao.get("avatar"));
	}

	/** timeout 非法时，已经存在的值应该原样保留 */
	@Test
	void set_shouldKeepOldValueWhenTimeoutInvalid() {
		dao.set("name", "zhangsan", 60);
		dao.set("name", "lisi", 0);
		Assertions.assertEquals("zhangsan", dao.get("name"));
	}

	/** timeout=-1 时应该永久存储，getTimeout 应该返回 NEVER_EXPIRE */
	@Test
	void set_shouldStoreNeverExpire() {
		dao.set("age", "20", SaTokenDao.NEVER_EXPIRE);
		Assertions.assertEquals("20", dao.get("age"));
		Assertions.assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("age"));
	}

	/** delete 之后应该取不到值 */
	@Test
	void delete_shouldRemoveValue() {
		dao.set("name", "zhangsan", 60);
		dao.delete("name");
		Assertions.assertNull(dao.get("name"));
	}

	/** 删除不存在的 key 时应该不抛异常 */
	@Test
	void delete_shouldIgnoreMissingKey() {
		Assertions.assertDoesNotThrow(() -> dao.delete("missing"));
	}

	/** 限时 key 的 getTimeout 应该返回剩余秒数 */
	@Test
	void getTimeout_shouldReturnRemainingSeconds() {
		dao.set("name", "zhangsan", 200);
		long timeout = dao.getTimeout("name");
		Assertions.assertTrue(timeout > 195 && timeout <= 200);
	}

	/** 不存在的 key 调用 getTimeout 应该返回 NOT_VALUE_EXPIRE */
	@Test
	void getTimeout_shouldReturnNotValueExpireWhenMissing() {
		Assertions.assertEquals(SaTokenDao.NOT_VALUE_EXPIRE, dao.getTimeout("missing"));
	}

	/** update 应该改值但保住原来的 TTL；对不存在的 key 应该什么都不做 */
	@Test
	void update_shouldChangeValueAndKeepTtl() {
		dao.set("name", "zhangsan", 200);
		dao.update("name", "lisi");
		Assertions.assertEquals("lisi", dao.get("name"));
		Assertions.assertTrue(dao.getTimeout("name") > 195);

		dao.update("missing", "wangwu");
		Assertions.assertNull(dao.get("missing"));
	}

	/** updateTimeout 应该改剩余存活时间 */
	@Test
	void updateTimeout_shouldChangeExpire() {
		dao.set("name", "zhangsan", 200);
		dao.updateTimeout("name", 500);
		long timeout = dao.getTimeout("name");
		Assertions.assertTrue(timeout > 495 && timeout <= 500);
	}

	/** 把限时 key 改成永久时，getTimeout 应该变成 NEVER_EXPIRE */
	@Test
	void updateTimeout_shouldConvertToNeverExpire() {
		dao.set("name", "zhangsan", 200);
		dao.updateTimeout("name", SaTokenDao.NEVER_EXPIRE);
		Assertions.assertEquals("zhangsan", dao.get("name"));
		Assertions.assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("name"));
	}

	/** 本来就是永久的 key，再 updateTimeout(-1) 时应该保持永久 */
	@Test
	void updateTimeout_shouldKeepNeverExpireWhenAlreadyPermanent() {
		dao.set("age", "20", SaTokenDao.NEVER_EXPIRE);
		dao.updateTimeout("age", SaTokenDao.NEVER_EXPIRE);
		Assertions.assertEquals("20", dao.get("age"));
		Assertions.assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("age"));
	}

	/** 已经初始化过的 Dao，再 init 一次不应该换掉 RedisTemplate */
	@Test
	void init_shouldSkipWhenAlreadyInitialized() {
		Assertions.assertTrue(dao.isInit);
		StringRedisTemplate first = dao.stringRedisTemplate;

		dao.init(connectionFactory);

		Assertions.assertTrue(dao.isInit);
		Assertions.assertSame(first, dao.stringRedisTemplate);
	}

	/** 重写 wrapKey 后，读写删超时都应该走包装后的 Redis 键 */
	@Test
	void wrapKey_shouldAffectGetSetDeleteTimeout() {
		SaTokenDaoForRedisTemplate prefixedDao = createPrefixedDao("app:");
		prefixedDao.set("name", "zhangsan", 60);

		Assertions.assertEquals("zhangsan", prefixedDao.get("name"));
		Assertions.assertEquals("zhangsan", dao.get("app:name"));
		Assertions.assertNull(dao.get("name"));
		Assertions.assertTrue(prefixedDao.getTimeout("name") > 0);

		prefixedDao.delete("name");
		Assertions.assertNull(prefixedDao.get("name"));
		Assertions.assertNull(dao.get("app:name"));
	}

	/** 改成永久时应该继续用原始 key 调 get/set，避免 wrap 两次变成 app:app:xxx */
	@Test
	void wrapKey_shouldNotDoubleWrapWhenUpdateTimeoutToNeverExpire() {
		SaTokenDaoForRedisTemplate prefixedDao = createPrefixedDao("app:");
		prefixedDao.set("name", "zhangsan", 60);

		prefixedDao.updateTimeout("name", SaTokenDao.NEVER_EXPIRE);

		Assertions.assertEquals("zhangsan", prefixedDao.get("name"));
		Assertions.assertEquals("zhangsan", dao.get("app:name"));
		Assertions.assertNull(dao.get("app:app:name"));
		Assertions.assertEquals(SaTokenDao.NEVER_EXPIRE, prefixedDao.getTimeout("name"));
	}

	/** 默认 wrapKey 应该原样返回 */
	@Test
	void wrapKey_shouldReturnOriginalKeyByDefault() {
		Assertions.assertEquals("satoken:login:token:abc", dao.wrapKey("satoken:login:token:abc"));
	}

	/** 创建连到当前内嵌 Redis 的 Lettuce 工厂，强制 RESP2 */
	private LettuceConnectionFactory createConnectionFactory() {
		RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("127.0.0.1", redisServer.getBindPort());
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
				.clientOptions(ClientOptions.builder()
						.protocolVersion(ProtocolVersion.RESP2)
						.build())
				.build();
		LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
		factory.afterPropertiesSet();
		return factory;
	}

	/** 清空当前库，避免用例互相脏数据 */
	private void flushDb() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

	/** 创建一个会给 Redis 键加前缀的 Dao */
	private SaTokenDaoForRedisTemplate createPrefixedDao(String prefix) {
		SaTokenDaoForRedisTemplate prefixedDao = new SaTokenDaoForRedisTemplate() {
			@Override
			public String wrapKey(String key) {
				return prefix + key;
			}
		};
		prefixedDao.init(connectionFactory);
		return prefixedDao;
	}

}
