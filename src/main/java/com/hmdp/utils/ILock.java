package com.hmdp.utils;

/**
 * Redis实现分布式锁
 * @author hyh
 * @date 2024/4/18
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
