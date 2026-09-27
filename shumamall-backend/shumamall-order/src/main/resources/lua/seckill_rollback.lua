-- 秒杀预扣回补（原子）：库存加回 + 按需释放一人一单占位。
-- 库存与资格占位的回补必须一起做：只加库存不释放资格，用户会被永久挡在门外；
-- 只释放资格不加库存，则额度凭空消失（少卖）。
--
-- KEYS[1] = 库存 key     seckill:stock:{activityId}
-- KEYS[2] = 用户资格 key seckill:user:{activityId}:{userId}
-- ARGV[1] = 回补数量
-- ARGV[2] = 是否释放资格占位：1=释放（建单失败，用户没成，允许重抢）
--                              0=保留（订单取消，额度退回但该用户本次参与已消耗）
-- 返回：回补后的库存值；若库存 key 已不存在（未预热/被清理）则返回 -1

if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local stock = redis.call('INCRBY', KEYS[1], ARGV[1])

if ARGV[2] == '1' then
    redis.call('DEL', KEYS[2])
end

return stock
