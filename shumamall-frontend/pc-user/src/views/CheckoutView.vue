<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { createOrder, getAddresses } from '../api';
import { isLoggedIn, store } from '../store';
import type { Address, OrderCreateDTO } from '../types';

const router = useRouter();
const addresses = ref<Address[]>([]);
const selectedAddressId = ref<number | null>(null);
const remark = ref('');
const submitting = ref(false);

/** 待结算商品：来自购物车勾选（带 id）或商品页立即购买（id=0） */
const checkoutItems = store.checkoutItems;
const fromCart = () => checkoutItems.some((i) => i.id > 0);

async function loadAddresses(): Promise<void> {
  try {
    addresses.value = await getAddresses();
    if (!selectedAddressId.value && addresses.value.length) {
      const def = addresses.value.find((a) => a.isDefault === 1) ?? addresses.value[0];
      selectedAddressId.value = def.id;
    }
  } catch (e) {
    ElMessage.error(`地址加载失败：${(e as Error).message}`);
  }
}

/** 提交订单：购物车结算走 cartItemIds，单品直接购买走 skuId + quantity */
async function submit(): Promise<void> {
  if (checkoutItems.length === 0) {
    ElMessage.warning('没有待结算的商品');
    return;
  }
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址');
    return;
  }
  submitting.value = true;
  try {
    const first = checkoutItems[0];
    const dto: OrderCreateDTO = {
      productId: first.productId,
      productName: first.productName,
      addressId: selectedAddressId.value,
      remark: remark.value || undefined,
    };
    if (fromCart()) {
      dto.cartItemIds = checkoutItems.map((i) => i.id);
    } else {
      dto.skuId = first.skuId;
      dto.quantity = first.quantity;
    }
    const order = await createOrder(dto);
    ElMessage.success(`下单成功，订单号：${order.orderNo}`);
    store.checkoutItems = [];
    remark.value = '';
    void router.push('/orders');
  } catch (e) {
    ElMessage.error(`下单失败：${(e as Error).message}`);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  if (isLoggedIn.value) {
    void loadAddresses();
  }
});
</script>

<template>
  <div class="checkout-page">
    <el-empty
      v-if="!isLoggedIn"
      description="请先登录再结算"
    />
    <el-empty
      v-else-if="checkoutItems.length === 0"
      description="没有待结算的商品，去商品页逛逛吧"
    >
      <el-button type="primary" @click="router.push('/')">去商品页</el-button>
    </el-empty>

    <template v-else>
      <!-- 商品清单 -->
      <el-card shadow="never" class="block">
        <template #header>待结算商品</template>
        <el-table :data="checkoutItems">
          <el-table-column prop="productName" label="商品" min-width="220" />
          <el-table-column prop="skuSpecs" label="规格" min-width="120" />
          <el-table-column label="单价" width="120">
            <template #default="{ row }">¥{{ row.price.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="小计" width="140">
            <template #default="{ row }">
              <span class="subtotal">¥{{ (row.price * row.quantity).toFixed(2) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 收货地址 -->
      <el-card shadow="never" class="block">
        <template #header>
          <div class="block-header">
            <span>收货地址</span>
            <el-button link type="primary" @click="router.push('/address')">管理地址</el-button>
          </div>
        </template>
        <el-empty v-if="addresses.length === 0" description="还没有收货地址，请先到「收货地址」页新增">
          <el-button type="primary" @click="router.push('/address')">去新增地址</el-button>
        </el-empty>
        <el-radio-group v-else v-model="selectedAddressId" class="addr-group">
          <el-radio
            v-for="a in addresses"
            :key="a.id"
            :value="a.id"
            class="addr-radio"
          >
            <span class="addr-name">{{ a.consignee }}</span>
            <span class="addr-phone">{{ a.phone }}</span>
            <span class="addr-detail">
              {{ a.province }}{{ a.city }}{{ a.district }}{{ a.detailAddress }}
            </span>
            <el-tag v-if="a.isDefault === 1" size="small" type="success">默认</el-tag>
          </el-radio>
        </el-radio-group>
      </el-card>

      <!-- 备注 + 提交 -->
      <el-card shadow="never" class="block">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="2"
          placeholder="订单备注（选填）"
          maxlength="200"
          show-word-limit
        />
        <div class="submit-row">
          <span class="total">
            应付合计：
            <span class="amount">¥{{ checkoutItems.reduce((s, i) => s + i.price * i.quantity, 0).toFixed(2) }}</span>
          </span>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">
            提交订单
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.checkout-page {
  padding: 16px 24px 24px;
}
.block {
  margin-bottom: 16px;
}
.block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.subtotal {
  color: #f56c6c;
  font-weight: 600;
}
.addr-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.addr-radio {
  height: auto;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-right: 0;
}
.addr-radio :deep(.el-radio__label) {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: normal;
}
.addr-name {
  font-weight: 600;
}
.addr-phone {
  color: #606266;
}
.addr-detail {
  color: #909399;
}
.submit-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 20px;
}
.total {
  font-size: 14px;
}
.amount {
  color: #f56c6c;
  font-weight: 700;
  font-size: 22px;
}
</style>
