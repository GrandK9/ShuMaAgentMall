<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createAddress, deleteAddress, getAddresses, updateAddress } from '../api';
import { isLoggedIn } from '../store';
import type { Address } from '../types';

const loading = ref(false);
const addresses = ref<Address[]>([]);

/* 新增 / 编辑弹窗 */
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  label: '',
  isDefault: 0,
});
const saving = ref(false);

async function load(): Promise<void> {
  loading.value = true;
  try {
    addresses.value = await getAddresses();
  } catch (e) {
    ElMessage.error(`地址加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function resetForm(): void {
  Object.assign(form, {
    consignee: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    label: '',
    isDefault: 0,
  });
  editingId.value = null;
}

function openCreate(): void {
  resetForm();
  dialogVisible.value = true;
}

function openEdit(a: Address): void {
  editingId.value = a.id;
  Object.assign(form, {
    consignee: a.consignee,
    phone: a.phone,
    province: a.province,
    city: a.city,
    district: a.district,
    detailAddress: a.detailAddress,
    label: a.label ?? '',
    isDefault: a.isDefault ?? 0,
  });
  dialogVisible.value = true;
}

async function save(): Promise<void> {
  if (!form.consignee || !form.phone || !form.detailAddress) {
    ElMessage.warning('请填写收货人、手机号和详细地址');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, { ...form });
      ElMessage.success('地址已更新');
    } else {
      await createAddress({ ...form });
      ElMessage.success('地址已新增');
    }
    dialogVisible.value = false;
    void load();
  } catch (e) {
    ElMessage.error(`保存失败：${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function onDelete(a: Address): Promise<void> {
  await ElMessageBox.confirm(`确定删除 ${a.consignee} 的地址吗？`, '提示', { type: 'warning' });
  try {
    await deleteAddress(a.id);
    ElMessage.success('已删除');
    void load();
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

/** 设为默认：整体更新 isDefault=1 */
async function onSetDefault(a: Address): Promise<void> {
  try {
    await updateAddress(a.id, {
      consignee: a.consignee,
      phone: a.phone,
      province: a.province,
      city: a.city,
      district: a.district,
      detailAddress: a.detailAddress,
      label: a.label,
      isDefault: 1,
    });
    ElMessage.success('已设为默认地址');
    void load();
  } catch (e) {
    ElMessage.error(`操作失败：${(e as Error).message}`);
  }
}

onMounted(() => {
  if (isLoggedIn.value) {
    void load();
  }
});
</script>

<template>
  <div class="addr-page">
    <el-empty v-if="!isLoggedIn" description="请先登录，再管理收货地址" />
    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="addr-header">
            <span>收货地址（{{ addresses.length }}）</span>
            <el-button type="primary" size="small" @click="openCreate">新增地址</el-button>
          </div>
        </template>

        <div v-loading="loading">
          <el-empty v-if="!loading && addresses.length === 0" description="暂无收货地址，点右上角新增" />
          <el-table v-else :data="addresses">
            <el-table-column prop="consignee" label="收货人" width="110" />
            <el-table-column prop="phone" label="手机号" width="140" />
            <el-table-column label="地址" min-width="260">
              <template #default="{ row }">
                {{ row.province }}{{ row.city }}{{ row.district }}{{ row.detailAddress }}
              </template>
            </el-table-column>
            <el-table-column label="标签" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.label" size="small" effect="plain">{{ row.label }}</el-tag>
                <el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.isDefault !== 1" link type="primary" @click="onSetDefault(row)">
                  设为默认
                </el-button>
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </template>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑地址' : '新增地址'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="收货人" required>
          <el-input v-model="form.consignee" placeholder="如：张三" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" placeholder="如：13800138000" />
        </el-form-item>
        <el-form-item label="省 / 市 / 区">
          <div class="region">
            <el-input v-model="form.province" placeholder="省" />
            <el-input v-model="form.city" placeholder="市" />
            <el-input v-model="form.district" placeholder="区" />
          </div>
        </el-form-item>
        <el-form-item label="详细地址" required>
          <el-input v-model="form.detailAddress" placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.label" placeholder="如：家 / 公司" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.addr-page {
  padding: 16px 24px 24px;
}
.addr-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.region {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
