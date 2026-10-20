# 数据格式

种子数据首次运行导入；日后以设备 SharedPreferences 中的数据为准。各设备的数据相互独立，尚无自动同步。

## 冰箱

```json
{
  "schemaVersion": 1,
  "fridges": [{
    "id": "kitchen",
    "name": "厨房冰箱",
    "inventoryStatus": "已部分录入",
    "items": [{
      "id": "example-batch",
      "name": "示例酸奶",
      "quantity": 2,
      "unit": "个",
      "location": "上部冷藏",
      "shelfLife": {"startDate": "2030-01-01", "endDate": "2030-01-10"}
    }]
  }]
}
```

保质期未知时 `shelfLife` 用 `null`，可添加 `reminder: "尽快食用"`。空 items 加「待录入」不代表冰箱没有食材。id 用稳定且唯一的字符串。

0.7.0 起食材 id 表示独立批次。旧数据没有 id 时会生成兼容标识，下一次保存时一同写入。即使同名，不同批次的数量与保质期也独立。开始日期可以留空；只有到期日期时界面显示「到期」。新增和扣减流程见 [冰箱语音](FRIDGE-VOICE.md)。

## 日程

```json
{
  "schemaVersion": 1,
  "timezone": "Asia/Shanghai",
  "tasks": [{
    "id": "example-task",
    "title": "示例事项",
    "date": "2030-01-01",
    "time": "17:00",
    "departureTime": "16:30",
    "note": "示例备注",
    "mustFinishToday": true,
    "completed": false
  }]
}
```

日期为 `yyyy-MM-dd`，时间为 24 小时制 `HH:mm`。无需出发时间时用空字符串。出发时间不能晚于事项时间。`mustFinishToday` 指定日期结束前完成，到点不会自动完成。当前实现固定使用上海时区，JSON 中的 timezone 是说明字段，不是可切换配置。


日程可包含 `createdAt`（首次创建的 Unix 毫秒时间戳）。旧数据在首次加载时用迁移时间和原列表顺序补齐，此值不代表历史真实创建时间；之后编辑不改变它。「即将到期」按计划时间排序，今日必办按当天结束排序；「最近添加」也显示已完成事件，供恢复或编辑。
