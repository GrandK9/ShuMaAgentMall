<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { clearCart, getCart, removeCartItem, updateCartQuantity, updateCartSelected } from '../api';
import { isLoggedIn, store } from '../store';
import type { CartItem } from '../types';

const router = useRouter();
const loading = ref(false);
const items = ref<CartItem[]>([]);

async function load(): Promise<void> {
  loading.value = true;
  try {
    items.value = await getCart();
  } catch (e) {
    ElMessage.error(`购物车加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function onQuantityChange(item: CartItem, qty: number): Promise<void> {
  if (qty < 1) {
    item.quantity = 1;
    return;
  }
  try {
    await updateCartQuantity(item.skuId, qty);
    item.quantity = qty;
    item.subtotal = item.price * qty;
  } catch (e) {
    item.quantity = qty; // 回滚为上一次值
    ElMessage.error(`修改数量失败：${(e as Error).message}`);
    void load();
  }
}

async function onSelectedChange(item: CartItem, selected: boolean): Promise<void> {
  try {
    await updateCartSelected(item.skuId, selected ? 1 : 0);
    item.selected = selected ? 1 : 0;
  } catch (e) {
    ElMessage.error(`更新选中失败：${(e as Error).message}`);
    void load();
  }
}

async function onRemove(item: CartItem): Promise<void> {
  try {
    await removeCartItem(item.skuId);
    items.value = items.value.filter((i) => i.skuId !== item.skuId);
    ElMessage.success('已删除');
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

async function onClear(): Promise<void> {
  await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' });
  try {
    await clearCart();
    items.value = [];
    ElMessage.success('购物车已清空');
  } catch (e) {
    ElMessage.error(`清空失败：${(e as Error).message}`);
  }
}

/** 已选中的购物车项（含合计） */
const selectedItems = () => items.value.filter((i) => i.selected === 1);
const selectedTotal = () => selectedItems().reduce((sum, i) => sum + i.subtotal, 0);

/** 去结算：把选中的购物车项写入 store，跳转结算页 */
function checkout(): void {
  const sel = selectedItems();
  if (sel.length === 0) {
    ElMessage.warning('请先勾选要结算的商品');
    return;
  }
  store.checkoutItems = sel.map((i) => ({ ...i }));
  void router.push('/checkout');
}

onMounted(() => {
  if (isLoggedIn.value) {
    void load();
  }
});
</script>

<template>
  <div class="cart-page">
    <el-empty
      v-if="!isLoggedIn"
      description="请先在右上角登录，再查看购物车"
    />
    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="cart-header">
            <span>我的购物车（{{ items.length }} 件）</span>
            <el-button v-if="items.length" link type="danger" @click="onClear">清空购物车</el-button>
          </div>
        </template>

        <div v-loading="loading">
          <el-empty v-if="!loading && items.length === 0" description="购物车空空如也，去商品页逛逛吧" />
          <el-table v-else :data="items">
            <el-table-column label="选择" width="70">
              <template #default="{ row }">
                <el-checkbox
                  :model-value="row.selected === 1"
                  @change="(v: string | number | boolean) => onSelectedChange(row, Boolean(v))"
                />
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="商品" min-width="220">
              <template #default="{ row }">
                <div class="product-cell">
                  <span class="pname">{{ row.productName }}</span>
                  <span v-if="row.skuSpecs" class="pspecs">{{ row.skuSpecs }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="120">
              <template #default="{ row }">¥{{ row.price.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="180">
              <template #default="{ row }">
                <el-input-number
                  :model-value="row.quantity"
                  :min="1"
                  :max="99"
                  size="small"
                  @change="(v: number | undefined) => onQuantityChange(row, v ?? 1)"
                />
              </template>
            </el-table-column>
            <el-table-column label="小计" width="130">
              <template #default="{ row }">
                <span class="subtotal">¥{{ row.subtotal.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="danger" @click="onRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 底部结算栏 -->
        <div v-if="items.length" class="cart-footer">
          <span class="total-label">
            已选 {{ selectedItems().length }} 件，合计：
            <span class="total-amount">¥{{ selectedTotal().toFixed(2) }}</span>
          </span>
          <el-button type="primary" size="large" @click="checkout">去结算</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.cart-page {
  padding: 16px 24px 24px;
}
.cart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.product-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pname {
  font-weight: 600;
}
.pspecs {
  font-size: 12px;
  color: #909399;
}
.subtotal {
  color: #f56c6c;
  font-weight: 600;
}
.cart-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 20px;
}
.total-label {
  font-size: 14px;
}
.total-amount {
  color: #f56c6c;
  font-weight: 700;
  font-size: 20px;
}
</style>
