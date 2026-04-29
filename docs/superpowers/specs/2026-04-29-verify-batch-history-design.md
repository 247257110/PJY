# 设计文档：Verify 页面新增历史解析批次列表

**日期：** 2026-04-29  
**状态：** 待实现

---

## 背景与目标

当前"项目验收材料校验"页面（`/verify`）仅支持当次上传解析，没有历史批次入口。用户上传后若关闭页面，之前解析但未入库的记录将无法继续处理。

**目标：** 在 Verify 页面显示当前用户/机构的历史解析批次，支持恢复预览和删除操作。

---

## 需求

- 列表显示字段：来源文件名、解析记录数、机构名称、上传时间
- 操作列：**进入预览**（恢复该批次数据到 Step 2）、**删除**（删除该批次临时数据）
- 权限：普通用户只看本机构批次；管理员看全部批次（显示机构名列）
- 数据范围：`temp_record` 表按 `batch_id` 分组，已入库（confirm）的批次不显示

---

## 页面布局

```
┌─────────────────────────────────────────────┐
│  历史解析批次                                  │
│  来源文件     记录数  机构     上传时间    操作   │
│  file.pdf     12     XX公司  2026-04-28  进入预览 删除 │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  步骤导航（Step 1 / 2 / 3）                   │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  第一步：上传材料                              │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐  ← v-if="batchId"
│  第二步：解析结果预览                           │
└─────────────────────────────────────────────┘
```

---

## 后端设计

### 新增接口：`GET /api/verify/batches`

按当前用户 `orgId` 查询 `temp_record` 表，按 `batch_id` 分组聚合。

**SQL（MyBatis XML）：**
```sql
SELECT
  batch_id,
  MAX(source_file)  AS sourceFile,
  MAX(org_name)     AS orgName,
  MAX(org_id)       AS orgId,
  COUNT(*)          AS recordCount,
  MIN(created_at)   AS createdAt
FROM temp_record
WHERE (#{isAdmin} = true OR org_id = #{orgId})
GROUP BY batch_id
ORDER BY MIN(created_at) DESC
```

**返回 DTO：**
```json
{
  "code": 200,
  "data": [
    {
      "batchId": "abc123",
      "sourceFile": "file.pdf",
      "orgName": "XX公司",
      "recordCount": 12,
      "createdAt": "2026-04-28T14:30:00"
    }
  ]
}
```

**Controller 方法：** `VerifyController.getBatches(Authentication auth)`  
**Mapper：** `TempRecordMapper.selectBatchSummary(Long orgId, boolean isAdmin)`

### 复用已有接口

- `GET /api/verify/preview/{batchId}` — 恢复批次数据（已存在）
- `DELETE /api/verify/cancel/{batchId}` — 删除批次（已存在）

---

## 前端设计

**文件：** `frontend/src/views/Verify.vue`

### 新增状态变量
```javascript
const batchList = ref([])        // 历史批次列表
const batchListLoading = ref(false)
```

### 新增 API 函数（api/index.js）
```javascript
export const verify = {
  // 已有 ...
  batches: () => http.get('/verify/batches'),
}
```

### 新增方法

```javascript
// 加载历史批次列表
async function loadBatches() {
  batchListLoading.value = true
  try {
    const res = await verify.batches()
    if (res.data.code === 200) batchList.value = res.data.data
  } catch { /* 不阻断主流程 */ }
  finally { batchListLoading.value = false }
}

// 进入预览（恢复批次）
async function enterPreview(batch) {
  const res = await verify.preview(batch.batchId)  // 调用已有预览接口
  if (res.data.code === 200) {
    batchId.value = batch.batchId
    previewData.value = res.data.data
    attendances.value = res.data.attendances || []
    checkDone.value = false
    await nextTick()
    previewCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

// 删除批次
async function deleteBatch(batch) {
  await ElMessageBox.confirm(`确认删除批次"${batch.sourceFile}"的解析数据？`)
  await verify.cancel(batch.batchId)
  ElMessage.success('已删除')
  if (batchId.value === batch.batchId) resetAll()
  loadBatches()
}
```

### onMounted 调整
```javascript
onMounted(() => {
  loadOrgList()
  loadBatches()
})
```

### 新增模板片段（放在步骤导航之前）
历史批次列表卡片，表格列：来源文件、记录数、机构名称（管理员可见）、上传时间、操作（进入预览 + 删除）。

---

## 数据流

```
onMounted
  → loadBatches() → GET /api/verify/batches
  → batchList 展示在历史卡片

用户点击"进入预览"
  → enterPreview(batch) → GET /api/verify/preview/{batchId}
  → 设置 batchId / previewData / attendances
  → 滚动到 Step 2

用户点击"删除"
  → 二次确认 → DELETE /api/verify/cancel/{batchId}
  → 刷新 batchList
  → 若删除当前批次则 resetAll()
```

---

## 错误处理

- `loadBatches` 失败静默处理（不阻断上传主流程）
- `enterPreview` 失败显示 `ElMessage.error`
- `deleteBatch` 失败显示 `ElMessage.error`

---

## 验证方案

1. 启动前后端
2. 上传一个文件并解析成功
3. 刷新页面，确认历史批次列表显示该批次
4. 点击"进入预览"，确认 Step 2 展开并显示解析数据
5. 点击"删除"，确认二次弹窗后批次从列表消失
6. 管理员登录验证可见所有机构批次
