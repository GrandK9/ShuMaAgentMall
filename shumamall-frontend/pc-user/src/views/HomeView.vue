<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
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

/** 首屏主视觉：销量榜首商品（hero-object 方向） */
const heroProduct = computed(() => hotProducts.value[0] ?? null);

/** 启用动效层（无 JS 时不隐藏文案） */
const homeMotionEnabled = ref(false);
/** 榜首 hero 入场（数据到位后再播，避免空白闪烁） */
const heroMotionPlay = ref(false);
let sectionRevealObserver: IntersectionObserver | null = null;
let productTileObserver: IntersectionObserver | null = null;

function bindSectionReveal(): void {
  sectionRevealObserver?.disconnect();
  sectionRevealObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed');
          sectionRevealObserver?.unobserve(entry.target);
        }
      }
    },
    { threshold: 0.1, rootMargin: '0px 0px -6% 0px' }
  );
  document.querySelectorAll('.home-page .motion-section').forEach((el) => {
    sectionRevealObserver?.observe(el);
  });
}

/** 商品列表：每张卡片进入视口时再上滑（ref 挂载时注册，避免 v-for 渲染晚于 querySelector） */
function ensureProductTileObserver(): IntersectionObserver | null {
  if (!homeMotionEnabled.value) return null;
  if (!productTileObserver) {
    productTileObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const el = entry.target as HTMLElement;
          productTileObserver?.unobserve(el);
          requestAnimationFrame(() => {
            el.classList.add('is-in-view');
          });
        }
      },
      { threshold: 0.06, rootMargin: '0px 0px -2% 0px' }
    );
  }
  return productTileObserver;
}

function setProductTileRef(el: unknown): void {
  if (!(el instanceof HTMLElement) || !el.classList.contains('scroll-rise')) return;
  if (!homeMotionEnabled.value) {
    el.classList.add('is-in-view');
    return;
  }
  const observer = ensureProductTileObserver();
  if (!observer) return;
  el.classList.remove('is-in-view');
  observer.observe(el);
}

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

watch(
  heroProduct,
  (p) => {
    heroMotionPlay.value = false;
    if (!p) return;
    void nextTick(() => {
      requestAnimationFrame(() => {
        heroMotionPlay.value = true;
      });
    });
  },
  { immediate: true }
);

watch(
  () => [hotProducts.value.length, hotComments.value.length, products.value.length, searchMode.value],
  () => {
    if (!homeMotionEnabled.value) return;
    void nextTick(bindSectionReveal);
  }
);

watch(
  () => products.value.map((p) => p.id).join(','),
  () => {
    productTileObserver?.disconnect();
    productTileObserver = null;
  }
);

onMounted(() => {
  homeMotionEnabled.value = true;
  void nextTick(bindSectionReveal);
  void loadCategories();
  void loadHotProducts();
  void loadHotComments();
  void load(1);
});

onUnmounted(() => {
  sectionRevealObserver?.disconnect();
  productTileObserver?.disconnect();
});
</script>

<template>
  <div class="home-page" :class="{ 'home-motion': homeMotionEnabled }" v-loading="loading">
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

    <section
      v-if="heroProduct && !searchMode"
      :key="heroProduct.id"
      class="catalog-hero"
      :class="{
        'catalog-hero--no-image': !heroProduct.mainImage,
        'hero-motion--play': heroMotionPlay,
      }"
    >
      <div
        class="hero-media hero-shutter"
        :class="{ 'hero-shutter--in': heroMotionPlay }"
        aria-hidden="true"
      >
        <img
          v-if="heroProduct.mainImage"
          class="hero-bg-img"
          :src="heroProduct.mainImage"
          alt=""
        />
        <div v-else class="hero-bg-fallback" />
        <div class="hero-scrim" />
      </div>
      <div class="hero-copy">
        <h1 class="hero-title hero-rise">{{ heroProduct.name }}</h1>
        <p class="hero-lead hero-rise hero-rise--1">
          销量榜首 · 本周 {{ heroProduct.salesVolume ?? 0 }} 件 ·
          <span class="amount">¥{{ heroProduct.price.toFixed(2) }}</span>
        </p>
        <el-button
          class="hero-cta hero-rise hero-rise--2"
          type="primary"
          size="large"
          @click="goDetail(heroProduct)"
        >
          查看这款
        </el-button>
      </div>
    </section>

    <section v-if="hotProducts.length > 1 && !searchMode" class="hot-section motion-section">
      <h2 class="section-title motion-rise">热销榜</h2>
      <div class="hot-rail">
        <article
          v-for="(p, idx) in hotProducts.slice(1)"
          :key="p.id"
          class="hot-tile"
          :style="{ '--delay': `${100 + idx * 70}ms` }"
          @click="goDetail(p)"
        >
          <span class="hot-rank motion-hot-part">{{ idx + 2 }}</span>
          <div class="hot-cover motion-hot-part motion-hot-part--cover">
            <img
              v-if="p.mainImage"
              class="hot-cover-img"
              :src="p.mainImage"
              :alt="p.name"
              loading="lazy"
            />
            <div v-else class="img-placeholder">暂无图片</div>
          </div>
          <p class="hot-name motion-hot-part motion-hot-part--name">{{ p.name }}</p>
          <p class="hot-price amount motion-hot-part motion-hot-part--price">
            ¥{{ p.price.toFixed(2) }}
          </p>
        </article>
      </div>
    </section>

    <!-- 热点板块：全站热门评论 Top 8 -->
    <section v-if="hotComments.length" class="hot-section comments-section motion-section">
      <h2 class="section-title motion-rise">买家在说</h2>
      <div class="hot-comments">
        <div
          v-for="(c, idx) in hotComments"
          :key="c.id"
          class="hot-comment motion-rise"
          :style="{ '--delay': `${120 + idx * 60}ms` }"
          @click="goProductDetail(c.productId)"
        >
          <span class="hot-comment-user">{{ c.username || `用户${c.userId ?? ''}` }}</span>
          <span class="hot-comment-content">{{ c.content }}</span>
          <span class="hot-comment-likes">赞 {{ c.likeCount ?? 0 }}</span>
        </div>
      </div>
    </section>

    <div class="home-body">
      <aside class="category-sidebar" aria-label="商品分类">
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

      <main class="catalog-main motion-section">
        <h2 class="section-title catalog-heading motion-rise">{{ searchMode ? '搜索结果' : '全部商品' }}</h2>
        <el-empty
          v-if="!loading && products.length === 0"
          :description="searchMode ? '未找到相关商品' : '暂无商品'"
        />
        <div v-else class="product-grid">
          <article
            v-for="(p, idx) in products"
            :key="p.id"
            :ref="setProductTileRef"
            class="product-tile scroll-rise"
            :style="{ '--delay': `${(idx % 4) * 70}ms` }"
            @click="goDetail(p)"
          >
            <div class="product-cover">
              <img v-if="p.mainImage" :src="p.mainImage" :alt="p.name" loading="lazy" />
              <div v-else class="img-placeholder">暂无图片</div>
            </div>
            <div class="product-info">
              <h3 class="name">{{ p.name }}</h3>
              <p class="price amount">¥{{ p.price.toFixed(2) }}</p>
              <p class="sales">销量 {{ p.salesVolume ?? 0 }}</p>
            </div>
            <div
              class="tile-actions"
              :class="{ 'tile-actions--single': !(p.skuList && p.skuList.length) }"
              @click.stop
            >
              <template v-if="p.skuList && p.skuList.length">
                <el-button class="tile-btn tile-btn-cart" size="small" @click="onAddCart(p)">加购</el-button>
                <el-button class="tile-btn tile-btn-buy" size="small" type="primary" @click="onBuyNow(p)">
                  购买
                </el-button>
              </template>
              <el-button v-else class="tile-btn" size="small" @click="goDetail(p)">详情</el-button>
            </div>
          </article>
        </div>
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
.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--surface-inset);
  border-radius: var(--radius-lg);
}
.search-tip {
  margin: calc(-1 * var(--space-2)) 0 var(--space-3);
  color: var(--muted);
  font-size: var(--text-sm);
}

.catalog-hero {
  position: relative;
  margin-bottom: var(--space-6);
  min-height: min(56vh, 440px);
  border-radius: var(--radius-lg);
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  padding: var(--space-6) var(--space-8);
}

.hero-media {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.hero-bg-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  transform-origin: center center;
}

.hero-bg-fallback {
  width: 100%;
  height: 100%;
  background: var(--surface-inset);
}

.hero-scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    102deg,
    color-mix(in srgb, var(--ink) 78%, transparent) 0%,
    color-mix(in srgb, var(--ink) 42%, transparent) 38%,
    color-mix(in srgb, var(--ink) 8%, transparent) 62%,
    transparent 100%
  );
}

.catalog-hero--no-image .hero-scrim {
  background: color-mix(in srgb, var(--surface-card) 18%, transparent);
}

.hero-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(28rem, 92%);
  padding-bottom: var(--space-2);
}

.hero-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--surface-card);
  max-width: 16ch;
  text-wrap: balance;
}

.catalog-hero--no-image .hero-title {
  color: var(--ink);
}

.hero-lead {
  margin: var(--space-4) 0 var(--space-5);
  font-size: var(--text-base);
  color: color-mix(in srgb, var(--surface-card) 88%, transparent);
  max-width: 36ch;
}

.catalog-hero--no-image .hero-lead {
  color: var(--muted);
}

@keyframes home-rise {
  from {
    opacity: 0;
    transform: translateY(28px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .hero-motion--play .hero-shutter {
    clip-path: inset(0 0 100% 0);
    transition: clip-path var(--t-open) cubic-bezier(0.76, 0, 0.24, 1);
  }
  .hero-motion--play .hero-shutter--in {
    clip-path: inset(0);
  }
  .hero-motion--play .hero-shutter .hero-bg-img,
  .hero-motion--play .hero-shutter .hero-bg-fallback {
    transform: scale(1.1);
    transition: transform calc(var(--t-open) + 200ms) var(--ease-soft);
  }
  .hero-motion--play .hero-shutter--in .hero-bg-img,
  .hero-motion--play .hero-shutter--in .hero-bg-fallback {
    transform: none;
  }

  .hero-motion--play .hero-rise {
    opacity: 0;
    transform: translateY(28px);
    animation: home-rise var(--t-reveal) var(--ease-out) forwards;
  }
  .hero-motion--play .hero-rise--1 {
    animation-delay: var(--stagger-line);
  }
  .hero-motion--play .hero-rise--2 {
    animation-delay: calc(var(--stagger-line) * 2);
  }

  .hero-cta {
    transition: transform var(--t-press) var(--ease-out);
  }
  .hero-cta:active {
    transform: scale(0.97);
  }

  .home-motion .motion-section:not(.is-revealed) .motion-rise {
    opacity: 0;
    transform: translateY(22px);
  }
  .home-motion .motion-section.is-revealed .motion-rise {
    animation: home-rise 720ms var(--ease-out) both;
    animation-delay: var(--delay, 0ms);
  }
  .home-motion .motion-section.is-revealed .section-title.motion-rise {
    animation-delay: 0ms;
  }

  .home-motion .product-tile.scroll-rise:not(.is-in-view) {
    opacity: 0;
    transform: translate3d(0, 32px, 0);
  }
  .home-motion .product-tile.scroll-rise.is-in-view {
    animation: tile-scroll-rise 720ms var(--ease-out) both;
    animation-delay: var(--delay, 0ms);
  }

  .home-motion .motion-section:not(.is-revealed) .motion-hot-part {
    opacity: 0;
    transform: translateY(18px);
  }
  .home-motion .motion-section.is-revealed .motion-hot-part {
    animation: home-rise 680ms var(--ease-out) both;
    animation-delay: var(--delay, 0ms);
  }
  .home-motion .motion-section.is-revealed .motion-hot-part--cover {
    animation-name: hot-cover-in;
    animation-duration: 760ms;
  }
  .home-motion .motion-section.is-revealed .motion-hot-part--name {
    animation-delay: calc(var(--delay, 0ms) + var(--stagger-item));
  }
  .home-motion .motion-section.is-revealed .motion-hot-part--price {
    animation-delay: calc(var(--delay, 0ms) + var(--stagger-item) + var(--stagger-line));
  }
}

@keyframes tile-scroll-rise {
  from {
    opacity: 0;
    transform: translate3d(0, 32px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes hot-cover-in {
  from {
    opacity: 0;
    transform: translateY(20px) scale(1.05);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.hot-section {
  margin-bottom: var(--space-6);
  padding: var(--space-5) var(--space-5) var(--space-6);
  background: var(--surface-raised);
  border-radius: var(--radius-lg);
}
.comments-section {
  background: var(--surface-raised);
}
.hot-rail {
  display: flex;
  gap: var(--space-4);
  overflow-x: auto;
  padding-bottom: var(--space-2);
  scroll-snap-type: x mandatory;
}
.hot-tile {
  flex: 0 0 176px;
  scroll-snap-align: start;
  cursor: pointer;
  position: relative;
  background: var(--surface-card);
  padding: var(--space-2);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  transition: background var(--duration-fast) var(--ease-out);
}
.hot-tile:hover {
  background: var(--surface-hover);
}
@media (prefers-reduced-motion: no-preference) {
  .hot-tile:hover .hot-cover {
    transform: translateY(-3px);
  }
  .hot-tile:hover .hot-cover-img {
    transform: scale(1.06);
  }
  .hot-tile:active .hot-cover {
    transform: translateY(-1px);
    transition-duration: var(--t-press);
  }
  .hot-tile:active .hot-cover-img {
    transform: scale(1.03);
    transition-duration: var(--t-press);
  }
}
.hot-rank {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  z-index: 1;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--muted);
  padding: 2px 6px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--surface-card) 88%, transparent);
}
.hot-cover {
  aspect-ratio: 1;
  width: 100%;
  flex-shrink: 0;
  background: var(--surface-inset);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: calc(var(--radius-md) - 2px);
  transition: transform var(--t-hover) var(--ease-out);
}
.hot-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  transform-origin: center center;
  transition: transform 520ms var(--ease-soft);
}
.hot-name {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-price {
  margin: 4px 0 0;
  font-weight: 600;
  color: var(--ink);
}

.hot-comments {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.hot-comment {
  display: grid;
  grid-template-columns: 100px 1fr auto;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--separator);
  cursor: pointer;
}
.hot-comment:last-child {
  border-bottom: none;
}
.hot-comment-user {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ink);
}
.hot-comment-content {
  font-size: var(--text-sm);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-comment-likes {
  font-size: var(--text-xs);
  color: var(--muted);
}

.home-body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-6);
  align-items: start;
}
.category-sidebar {
  position: sticky;
  top: 80px;
  background: var(--surface-raised);
  border-radius: var(--radius-lg);
  padding: var(--space-2) 0;
  max-height: calc(100vh - 96px);
  overflow-y: auto;
}
.cat-item {
  padding: 12px var(--space-4);
  cursor: pointer;
  font-size: var(--text-sm);
  color: var(--muted);
  border-radius: var(--radius-md);
  margin: 0 var(--space-2);
  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.cat-item:hover {
  color: var(--ink);
  background: color-mix(in srgb, var(--surface) 80%, var(--surface-card));
}
.cat-item.active {
  color: var(--ink);
  font-weight: 600;
  background: var(--surface-card);
}
.cat-item.sub {
  padding-left: var(--space-8);
  font-size: var(--text-xs);
}

.catalog-main {
  min-width: 0;
  background: var(--surface-raised);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
}
.catalog-heading {
  margin-bottom: var(--space-4);
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}
.product-tile {
  cursor: pointer;
  background: var(--surface-card);
  padding: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: background var(--duration-fast) var(--ease-out);
}
.product-tile:hover {
  background: var(--surface-hover);
}
@media (prefers-reduced-motion: no-preference) {
  .product-tile:hover .product-cover img {
    transform: scale(1.04);
  }
}
.product-cover {
  height: 168px;
  background: var(--surface-inset);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.product-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform-origin: center center;
  transition: transform 520ms var(--ease-soft);
}
.img-placeholder {
  color: var(--muted);
  font-size: var(--text-sm);
}
.product-info {
  padding: var(--space-3) var(--space-3) var(--space-4);
  flex: 1;
}
.product-info .name {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-info .price {
  margin: 6px 0 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--ink);
}
.product-info .sales {
  margin: 4px 0 0;
  font-size: var(--text-xs);
  color: var(--muted);
}
.tile-actions {
  margin-top: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3) var(--space-3);
  border-top: 1px solid var(--separator);
}

.tile-actions--single {
  grid-template-columns: 1fr;
}

.tile-actions :deep(.el-button) {
  width: 100%;
  margin: 0;
  font-weight: 600;
  transition: transform var(--t-press) var(--ease-out);
}
.tile-actions :deep(.el-button:active) {
  transform: scale(0.97);
}

.tile-actions :deep(.tile-btn-cart.el-button) {
  --el-button-bg-color: var(--surface-card);
  --el-button-border-color: color-mix(in srgb, var(--ink) 18%, transparent);
  --el-button-text-color: var(--ink);
  --el-button-hover-bg-color: var(--surface);
  --el-button-hover-border-color: var(--accent);
  --el-button-hover-text-color: var(--accent);
}

.tile-actions :deep(.tile-btn-buy.el-button--primary) {
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
}
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}

@media (max-width: 768px) {
  .catalog-hero {
    min-height: min(48vh, 360px);
    padding: var(--space-5);
    align-items: flex-end;
  }
  .hero-scrim {
    background: linear-gradient(
      180deg,
      color-mix(in srgb, var(--ink) 15%, transparent) 0%,
      color-mix(in srgb, var(--ink) 75%, transparent) 100%
    );
  }
  .catalog-hero--no-image .hero-scrim {
    background: linear-gradient(180deg, transparent 30%, color-mix(in srgb, var(--surface-card) 50%, transparent) 100%);
  }
  .home-body {
    grid-template-columns: 1fr;
  }
  .category-sidebar {
    position: static;
    max-height: none;
  }
}
</style>
