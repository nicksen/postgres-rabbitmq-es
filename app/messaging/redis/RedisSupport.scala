package messaging.redis

import play.api.Logger
import redis.clients.jedis.{Jedis, JedisPool, JedisPubSub}

import scala.concurrent.blocking

trait RedisSupport {
  private[this] val logger = Logger(classOf[RedisSupport])

  protected def jedisPool: JedisPool

  // Execute a Redis command with a connection from the pool.
  protected[this] def withJedis[A](f: Jedis => A): A = blocking {
    val jedis = jedisPool.getResource
    try {
      f(jedis)
    } finally {
      jedis.close()
    }
  }

  protected[this] def subscribeToChannels(channels: String*)(jedisPubSub: JedisPubSub): Unit = blocking {
    val jedis = jedisPool.getResource
    try {
      jedis.subscribe(jedisPubSub, channels: _*)
    } finally {
      jedis.close()
    }
  }

  protected[this] trait RedisPubSubNoOps {
    this: JedisPubSub =>
    override def onSubscribe(channel: String, subscribedChannels: Int) {}

    override def onMessage(channel: String, message: String) {}

    override def onPMessage(pattern: String, channel: String, message: String) {}

    override def onUnsubscribe(channel: String, subscribedChannels: Int) {}

    override def onPSubscribe(pattern: String, subscribedChannels: Int) {}

    override def onPUnsubscribe(pattern: String, subscribedChannels: Int) {}
  }

  protected class LuaScript(val script: String) {
    val id = withJedis {
      _.scriptLoad(script)
    }
    logger.trace(s"Loaded Lua script $script with id $id")

    def eval(keys: String*)(args: String*) = withJedis {
      _.evalsha(id, keys.size, keys ++ args: _*)
    }
  }

}
