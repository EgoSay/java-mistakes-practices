package com.cjw.github.mistakes.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <pre>
 *     使用 ThreadLocal 可能出现的问题
 *     1、线程池复用可能造成 ThreadLocal 脏数据
 * </pre>
 *
 * @author chenjiawei
 * @version 1.0
 * @date 2021/7/12 19:59
 */
public class ThreadLocalMistake {

    private static final Logger logger = LoggerFactory.getLogger(ThreadLocalMistake.class);

    private static final ThreadLocal<Integer> currentUser = ThreadLocal.withInitial(() -> null);

    public static void main(String[] args) throws InterruptedException {
        ThreadLocalMistake mistake = new ThreadLocalMistake();
        mistake.wrongExample();
        System.exit(1);
    }

    public void wrongExample() throws InterruptedException {
        // 线程池会重用固定的几个线程，一旦线程重用，那么很可能首次从 ThreadLocal 获取的值是之前其他用户的请求遗留的值
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(() -> getUserId(1));
        Thread.sleep(1000);
        executorService.execute(() -> getUserId(2));
        System.exit(1);
    }

    private void getUserId(int userId) {
        // 设置用户信息之前先查询一次 ThreadLocal中的用户信息
        String before = Thread.currentThread().getName() + ":" + currentUser.get();
        logger.info("before:" + before);
        // 设置用户信息到ThreadLocal
        currentUser.set(userId);
        // 设置用户信息之后再查询一次 ThreadLocal中的用户信息
        String after = Thread.currentThread().getName() + ":" + currentUser.get();
        logger.info("after:" + after);
    }
}
