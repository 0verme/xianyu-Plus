import { request } from '@/utils/request';

export interface KamiConfig {
  id: number;
  xianyuAccountId?: number | null;
  aliasName: string;
  /** 1=本地库存，2=外部 API，3=固定内容 */
  sourceType?: number;
  fixedContent?: string;
  deliveryTemplate?: string;
  relatedGoodsCount?: number;
  apiUrl?: string;
  apiMethod?: 'GET' | 'POST' | string;
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
  totalCount: number;
  usedCount: number;
  availableCount: number;
  createTime: string;
  updateTime: string;
}

export interface KamiItem {
  id: number;
  kamiConfigId: number;
  kamiContent: string;
  status: number;
  orderId: string | null;
  usedTime: string | null;
  sortOrder: number;
  createTime: string;
}

export interface KamiConfigDeleteResponse {
  message: string;
  historyCount: number;
  historyDeletionRequired: boolean;
}

export interface KamiArchivePreview {
  deliveredCount: number;
  archivableCount: number;
  missingHistoryCount: number;
  reservedCount: number;
  reviewRequiredCount: number;
}

export interface KamiArchiveResult {
  archivedCount: number;
  skippedMissingHistoryCount: number;
}

export interface KamiUsageHistory {
  id: number;
  kamiConfigId: number;
  kamiItemId: number | null;
  orderId: string;
  buyerUserId?: string | null;
  buyerUserName?: string | null;
  goodsId?: string | null;
  deliveryIndex: number;
  deliveryStatus: string;
  kamiContent: string;
  contentRevealed: boolean;
  deliveryTime: string;
}

export interface KamiUsageHistoryPage {
  records: KamiUsageHistory[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface SaveKamiConfigReq {
  id?: number;
  xianyuAccountId?: number | null;
  aliasName?: string;
  sourceType?: number;
  fixedContent?: string;
  deliveryTemplate?: string;
  apiUrl?: string;
  apiMethod?: 'GET' | 'POST';
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
}

export interface QueryKamiItemsReq {
  kamiConfigId: number;
  status?: number;
  keyword?: string;
}

export function saveKamiConfig(data: SaveKamiConfigReq) {
  return request<KamiConfig>({
    url: '/kami-config/save',
    method: 'POST',
    data
  });
}

export function getKamiConfigs() {
  return request<KamiConfig[]>({
    url: '/kami-config/list',
    method: 'POST'
  });
}

export function getKamiConfigById(id: number) {
  return request<KamiConfig>({
    url: '/kami-config/detail',
    method: 'POST',
    params: { id }
  });
}

export function deleteKamiConfig(id: number, confirmHistoryDeletion = false) {
  return request<KamiConfigDeleteResponse>({
    url: '/kami-config/delete',
    method: 'POST',
    params: { id, confirmHistoryDeletion }
  });
}

export interface KamiApiTestReq {
  apiUrl: string;
  apiMethod?: 'GET' | 'POST';
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
}

export interface KamiApiTestResult {
  statusCode: number;
  content: string;
  message: string;
}

export function testKamiApi(data: KamiApiTestReq) {
  return request<KamiApiTestResult>({
    url: '/kami-config/test-api',
    method: 'POST',
    data
  });
}

export interface KamiRelatedGoods {
  xianyuAccountId: number;
  xianyuGoodsId: number;
  xyGoodsId: string;
  accountNote?: string;
  goodsTitle?: string;
  coverPic?: string;
  soldPrice?: string;
  status?: number;
  associated?: boolean;
  willReplace?: boolean;
}

export function getKamiRelatedGoods(kamiConfigId: number) {
  return request<KamiRelatedGoods[]>({
    url: '/kami-config/related-goods/list',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function saveKamiRelatedGoods(data: { kamiConfigId: number; goods: KamiRelatedGoods[] }) {
  return request<number>({
    url: '/kami-config/related-goods/save',
    method: 'POST',
    data
  });
}

export function addKamiItem(data: { kamiConfigId: number; kamiContent: string }) {
  return request<KamiItem>({
    url: '/kami-config/item/add',
    method: 'POST',
    data
  });
}

export function batchImportKamiItems(data: { kamiConfigId: number; kamiContents: string }) {
  return request<number>({
    url: '/kami-config/item/batchImport',
    method: 'POST',
    data
  });
}

export function getKamiItemsByConfigId(kamiConfigId: number) {
  return request<KamiItem[]>({
    url: '/kami-config/item/list',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function queryKamiItems(data: QueryKamiItemsReq) {
  return request<KamiItem[]>({
    url: '/kami-config/item/query',
    method: 'POST',
    data
  });
}

export function deleteKamiItem(id: number) {
  return request({
    url: '/kami-config/item/delete',
    method: 'POST',
    params: { id }
  });
}
export function batchDeleteKamiItems(ids: number[]) {
  return request<number>({
    url: '/kami-config/item/batch-delete',
    method: 'POST',
    data: ids
  });
}

export function batchResetKamiItems(ids: number[]) {
  return request<number>({
    url: '/kami-config/item/batch-reset',
    method: 'POST',
    data: ids
  });
}
export function clearUsedKamiItems(kamiConfigId: number) {
  return request<number>({
    url: '/kami-config/item/clear-used',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function previewUsedKamiItems(kamiConfigId: number) {
  return request<KamiArchivePreview>({
    url: '/kami-config/item/archive-used/preview',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function archiveUsedKamiItems(kamiConfigId: number) {
  return request<KamiArchiveResult>({
    url: '/kami-config/item/archive-used',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function queryKamiUsageHistory(data: {
  kamiConfigId: number;
  orderId?: string;
  buyerKeyword?: string;
  goodsId?: string;
  deliveryStatus?: string;
  startTime?: string;
  endTime?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request<KamiUsageHistoryPage>({
    url: '/kami-usage-history/page',
    method: 'POST',
    data
  });
}

export function getKamiUsageHistoryDetail(id: number) {
  return request<KamiUsageHistory>({
    url: '/kami-usage-history/detail',
    method: 'POST',
    params: { id }
  });
}

export function resetKamiItem(id: number) {
  return request({
    url: '/kami-config/item/reset',
    method: 'POST',
    params: { id }
  });
}

export function exportKamiItems(data: { kamiConfigId: number; includeUnused: boolean; includeUsed: boolean }) {
  return request<KamiItem[]>({
    url: '/kami-config/item/export',
    method: 'POST',
    data
  });
}
