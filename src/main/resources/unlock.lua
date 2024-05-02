-- 根据key获取value（锁标识），并和当前线程标识比较，相同则释放锁
-- 这里的KEYS[1]是redis里的key， 根据key得到value并与ARGV[1]比较
if(redis.call("get", KEYS[1]) == ARGV[1]) then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
return 0

