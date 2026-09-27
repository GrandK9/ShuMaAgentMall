-- 秒杀库存预扣（原子）：库存校验 + 一人一单占位 + 扣减，三件事在一次脚本执行内完成。
-- Redis 以单线程执行脚本，脚本内部不会与其他命令交错，因此不存在"判断通过后、扣减之前被插队"的竞态；
-- 拆成 GET / SETNX / DECRBY 三条客户端命令则必然出现超卖窗口。
--
-- KEYS[1] = 库存 key     seckill:stock:{activityId}
-- KEYS[2] = 用户资格 key seckill:user:{activityId}:{userId}
-- ARGV[1] = 扣减数量
-- ARGV[2] = 资格 key 的 TTL（秒）
-- 返回：1=成功  -1=未预热  -2=库存不足  -3=重复下单

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1
end

if tonumber(stock) < tonumber(ARGV[1]) then
    return -2
end

-- 顺序讲究：先判库存再占资格。反过来的话，售罄时用户已经被标记为"已参与"，
-- 等于"没抢到却被判重复下单"，之后即使有库存回补也抢不了。
if redis.call('SETNX', KEYS[2], '1') == 0 then
    return -3
end

redis.call('EXPIRE', KEYS[2], ARGV[2])
redis.call('DECRBY', KEYS[1], ARGV[1])
return 1
