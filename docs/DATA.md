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
