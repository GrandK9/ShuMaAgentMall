<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { addCart, getCategoryTree, getHotComments, getProductPage, searchProducts } from '../api';
import { isLoggedIn, store } from '../store';
import type { CartItem, Category, Comment, Product, ProductSearchItem } from '../types';

const router = useRouter();
const loading = ref(false);
const products = ref<Product[]>([]);
const categories = ref<Category[]>([]);
const activeCategoryId = ref<number | null>(null);
const keyword = ref('');
const sortBy = ref<'default' | 'sales' | 'priceAsc' | 'priceDesc'>('default');
const total = ref(0);
const page = ref(1);
const size = 12;
/** 当前列表是否来自 ES 全文检索（检索 VO 不含 SKU，卡片不展示加购按钮） */
const searchMode = ref(false);

/** 热点板块：热门商品（销量 Top 8） */
const hotProducts = ref<Product[]>([]);
/** 热点板块：全站热门评论 Top 8 */
const hotComments = ref<Comment[]>([]);

const sortMap: Record<typeof sortBy.value, { sortBy?: string; sortOrder?: string }> = {
  default: {},
  sales: { sortBy: 'sales_volume', sortOrder: 'desc' },
  priceAsc: { sortBy: 'price', sortOrder: 'asc' },
  priceDesc: { sortBy: 'price', sortOrder: 'desc' },
};

/** 前端排序标签 → search 服务 sortBy 取值 */
const esSortMap: Record<typeof sortBy.value, string> = {
  default: 'default',
  sales: 'sales',
  priceAsc: 'price_asc',
  priceDesc: 'price_desc',
};

async function loadCategories(): Promise<void> {
  try {
    categories.value = await getCategoryTree();
  } catch (e) {
    ElMessage.error(`分类加载失败：${(e as Error).message}`);
  }
}

async function loadHotProducts(): Promise<void> {
  try {
    const res = await getProductPage({ sortBy: 'sales_volume', sortOrder: 'desc', size: 8 });
    hotProducts.value = res.records;
  } catch (e) {
    ElMessage.error(`热门商品加载失败：${(e as Error).message}`);
  }
}

async function loadHotComments(): Promise<void> {
  try {
    hotComments.value = await getHotComments(8);
  } catch (e) {
    ElMessage.error(`热门评论加载失败：${(e as Error).message}`);
  }
}

async function load(pageNo = 1): Promise<void> {
  loading.value = true;
  const kw = keyword.value.trim();
  try {
    if (kw) {
      // 有关键词：优先走 ES 全文检索（IK 分词 + 分类过滤 + 排序）
      try {
        const res = await searchProducts({
          keyword: kw,
          categoryId: activeCategoryId.value ?? undefined,
          sortBy: esSortMap[sortBy.value],
          page: pageNo,
          size,
        });
        products.value = res.records.map(toProduct);
        total.value = res.total;
        searchMode.value = true;
      } catch (e) {
        ElMessage.warning(`检索服务不可用，已降级为数据库匹配：${(e as Error).message}`);
        await loadFromDb(pageNo, kw);
      }
    } else {
      await loadFromDb(pageNo);
    }
    page.value = pageNo;
  } catch (e) {
    ElMessage.error(`商品加载失败：${(e as Error).message}`);
  } finally {
    loading.value = false;
  }
}

/** 商品分页接口兜底（无关键词浏览 / ES 不可用时降级） */
async function loadFromDb(pageNo: number, kw?: string): Promise<void> {
  const query: Record<string, unknown> = {
    page: pageNo,
    size,
    keyword: kw,
    ...sortMap[sortBy.value],
  };
  if (activeCategoryId.value) query.categoryId = activeCategoryId.value;
  const res = await getProductPage(query);
  products.value = res.records;
  total.value = res.total;
  searchMode.value = false;
}

/** ES 检索结果项 → 商品展示模型（检索 VO 无 SKU，故不补 skuList） */
function toProduct(it: ProductSearchItem): Product {
  return {
    id: it.productId,
    name: it.name,
    subtitle: it.subtitle,
    mainImage: it.mainImage,
    price: it.price ?? 0,
    salesVolume: it.salesVolume,
  };
}

function onCategoryClick(id: number | null): void {
  activeCategoryId.value = id;
  void load(1);
}

function onSearch(): void {
  void load(1);
}

function goDetail(p: Product): void {
  router.push(`/product/${p.id}`);
}

/** 从热门评论/热门商品跳转商品详情 */
function goProductDetail(productId: number): void {
  router.push(`/product/${productId}`);
}

async function onAddCart(p: Product): Promise<void> {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录');
    return;
  }
  const sku = p.skuList?.find((s) => (s.stock ?? 0) > 0) ?? p.skuList?.[0];
  if (!sku) {
    ElMessage.warning('该商品暂无 SKU');
    return;
  }
  try {
    await addCart({ skuId: sku.id, productId: p.id, quantity: 1 });
    ElMessage.success('已加入购物车');
  } catch (e) {
    ElMessage.error(`加购失败：${(e as Error).message}`);
  }
}

function onBuyNow(p: Product): void {
  const sku = p.skuList?.find((s) => (s.stock ?? 0) > 0) ?? p.skuList?.[0];
  if (!sku) {
    ElMessage.warning('该商品暂无 SKU');
    return;
  }
  const item: CartItem = {
    id: 0, // 立即购买：非购物车项，id=0，结算时走 skuId + quantity
    skuId: sku.id,
    productId: p.id,
    productName: p.name,
    productImage: p.mainImage,
    skuSpecs: sku.specs,
    price: sku.price,
    quantity: 1,
    subtotal: sku.price * 1,
  };
  store.checkoutItems = [item];
  router.push('/checkout');
}

onMounted(() => {
  void loadCategories();
  void loadHotProducts();
  void loadHotComments();
  void load(1);
});
</script>

<template>
  <div class="home-page" v-loading="loading">
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索商品" clearable @keyup.enter="onSearch" style="width: 360px;" />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-radio-group v-model="sortBy" @change="load(1)" style="margin-left: auto;">
        <el-radio-button value="default">综合</el-radio-button>
        <el-radio-button value="sales">销量</el-radio-button>
        <el-radio-button value="priceAsc">价格 ↑</el-radio-button>
        <el-radio-button value="priceDesc">价格 ↓</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="searchMode" class="search-tip">
      全文检索「{{ keyword }}」命中 {{ total }} 件商品
    </div>

    <!-- 热点板块：热门商品（销量 Top 8） -->
    <section v-if="hotProducts.length" class="hot-section">
      <div class="hot-title">热门商品</div>
      <div class="hot-products">
        <el-card v-for="(p, idx) in hotProducts" :key="p.id" shadow="hover" class="hot-card" @click="goDetail(p)">
          <div class="hot-rank" :class="idx < 3 ? 'top' : ''">{{ idx + 1 }}</div>
          <div class="hot-cover">
            <img v-if="p.mainImage" :src="p.mainImage" :alt="p.name" />
            <div v-else class="img-placeholder">暂无图片</div>
          </div>
          <div class="hot-name">{{ p.name }}</div>
          <div class="hot-price">¥{{ p.price.toFixed(2) }}</div>
          <div class="hot-sales">销量 {{ p.salesVolume ?? 0 }}</div>
        </el-card>
      </div>
    </section>

    <!-- 热点板块：全站热门评论 Top 8 -->
    <section v-if="hotComments.length" class="hot-section">
      <div class="hot-title">热门评论</div>
      <div class="hot-comments">
        <div v-for="c in hotComments" :key="c.id" class="hot-comment" @click="goProductDetail(c.productId)">
          <span class="hot-comment-user">{{ c.username || `用户${c.userId ?? ''}` }}</span>
          <span class="hot-comment-content">{{ c.content }}</span>
          <span class="hot-comment-likes">赞 {{ c.likeCount ?? 0 }}</span>
        </div>
      </div>
    </section>

    <div class="home-body">
      <aside class="category-sidebar">
        <div class="cat-item" :class="{ active: activeCategoryId === null }" @click="onCategoryClick(null)">全部分类</div>
        <div v-for="cat in categories" :key="cat.id">
          <div
            class="cat-item"
            :class="{ active: activeCategoryId === cat.id }"
            @click="onCategoryClick(cat.id)"
          >
            {{ cat.name }}
          </div>
          <div
            v-for="child in cat.children"
            :key="child.id"
            class="cat-item sub"
            :class="{ active: activeCategoryId === child.id }"
            @click="onCategoryClick(child.id)"
          >
            {{ child.name }}
          </div>
        </div>
      </aside>

      <main class="product-grid">
        <el-empty
          v-if="!loading && products.length === 0"
          :description="searchMode ? '未找到相关商品' : '暂无商品'"
        />
        <el-card v-for="p in products" :key="p.id" shadow="hover" class="product-card" @click="goDetail(p)">
          <div class="product-cover">
            <img v-if="p.mainImage" :src="p.mainImage" :alt="p.name" />
            <div v-else class="img-placeholder">暂无图片</div>
          </div>
          <div class="product-info">
            <div class="name">{{ p.name }}</div>
            <div class="price">¥{{ p.price.toFixed(2) }}</div>
            <div class="sales">销量 {{ p.salesVolume ?? 0 }}</div>
          </div>
          <div class="actions" @click.stop>
            <template v-if="p.skuList && p.skuList.length">
              <el-button size="small" @click="onAddCart(p)">加购</el-button>
              <el-button size="small" type="primary" @click="onBuyNow(p)">立即购买</el-button>
            </template>
            <el-button v-else size="small" @click="goDetail(p)">查看详情</el-button>
          </div>
        </el-card>
      </main>
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.home-page {
  padding: 16px 24px;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.search-tip {
  margin: -4px 0 12px;
  color: #909399;
  font-size: 13px;
}
/* 热点板块 */
.hot-section {
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}
.hot-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
}
.hot-products {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.hot-card {
  cursor: pointer;
  position: relative;
}
.hot-rank {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 4px;
  background: #909399;
  color: #fff;
  font-size: 12px;
  z-index: 1;
}
.hot-rank.top {
  background: #f56c6c;
}
.hot-cover {
  height: 120px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.hot-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hot-name {
  margin-top: 8px;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-price {
  color: #f56c6c;
  font-weight: bold;
  margin-top: 4px;
}
.hot-sales {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.hot-comments {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.hot-comment {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f8f9fb;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.hot-comment:hover {
  background: #ecf5ff;
}
.hot-comment-user {
  flex-shrink: 0;
  color: #409eff;
  font-size: 13px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-comment-content {
  flex: 1;
  color: #303133;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-comment-likes {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
}
.home-body {
  display: flex;
  gap: 16px;
  min-height: 400px;
}
.category-sidebar {
  width: 180px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  padding: 8px 0;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
.cat-item {
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
}
.cat-item:hover,
.cat-item.active {
  color: #409eff;
  background: #ecf5ff;
}
.cat-item.sub {
  padding-left: 32px;
  font-size: 13px;
}
.product-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.product-card {
  cursor: pointer;
}
.product-cover {
  height: 160px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.product-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.img-placeholder {
  color: #909399;
  font-size: 14px;
}
.product-info {
  padding: 12px 0;
}
.product-info .name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-info .price {
  color: #f56c6c;
  font-weight: bold;
  margin-top: 6px;
}
.product-info .sales {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
