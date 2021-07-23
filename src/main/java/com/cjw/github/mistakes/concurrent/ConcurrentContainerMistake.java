package com.cjw.github.mistakes.concurrent;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * <pre>
 *     并发容器使用
 *     1、使用了线程安全的并发工具，并不代表解决了所有线程安全问题
 *     2、ConcurrentHashMap 可以取保自己是线程安全，但不能确保用户对 ConcurrentHashMap【读取 存储 再读取 再存储】这整个动作是原子性的。
 * </pre>
 *
 * @author chenjw
 * @version 1.0
 * @date 2021/7/13 11:24
 */
public class ConcurrentContainerMistake {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentContainerMistake.class);
    private static final int LOOP_COUNT = 10000000;

    /**
     * 线程个数
     */
    private static int THREAD_COUNT = 10;
    /**
     * 总元素数量
     */
    private static int ITEM_COUNT = 1000;

    public static void main(String[] args) throws InterruptedException {
        ConcurrentContainerMistake mistake = new ConcurrentContainerMistake();
        mistake.wrongExample();
        mistake.statisticsKeyFrequency();
    }

    private ConcurrentHashMap<String, Long> getData(int count) {
        return LongStream.rangeClosed(1, count).boxed()
                .collect(Collectors.toConcurrentMap(i -> UUID.randomUUID().toString(),
                        Function.identity(), (o1, o2) -> o1, ConcurrentHashMap::new));
    }

    /**
     * 错误的用法示例
     * @throws InterruptedException
     */
    public void wrongExample() throws InterruptedException {
        ConcurrentHashMap<String, Long> concurrentHashMap = getData(ITEM_COUNT - 100);
        //初始900个元素
        logger.info("init size:{}", concurrentHashMap.size());

        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);
        // 使用线程池并发处理逻辑
        forkJoinPool.execute(() -> IntStream.rangeClosed(1, THREAD_COUNT).parallel().forEach(i -> {
            // 查询还需要填充多少个元素
            int gap = ITEM_COUNT - concurrentHashMap.size();
            logger.info("gap size:{}", gap);
            // 补充元素
            concurrentHashMap.putAll(getData(gap));
        }));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);

        // 最后的元素不一定是 1000
        logger.info("finished size:{}", concurrentHashMap.size());
    }

    /**
     * 使用 ConcurrentHashMap 来统计 Key 出现次数的场景
     * 1、使用最多 10 个并发，循环操作 1000 万次，每次操作累加随机的 Key
     * 2、如果 Key 不存在的话，首次设置值为 1
     */
    public void statisticsKeyFrequency() throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> frequency = new ConcurrentHashMap<>(ITEM_COUNT);
        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);

        forkJoinPool.execute(() -> IntStream.rangeClosed(1, LOOP_COUNT).parallel().forEach(i -> {
            String key = "item" + ThreadLocalRandom.current().nextInt(ITEM_COUNT);
            // 利用computeIfAbsent()方法来实例化LongAdder，然后利用LongAdder来进行线程安全计数
            frequency.computeIfAbsent(key, v -> new LongAdder()).increment();
        }));
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        Map<String, Long> result = frequency.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
        // 验证是否循环了1000万次，填充了1000万个元素
        Assert.isTrue(result.values().stream()
                .mapToLong(aLong -> aLong)
                .reduce(0, Long::sum) == LOOP_COUNT, "ok");
        logger.info("\nthe result is:{}", JSONObject.toJSON(result));
    }

}
