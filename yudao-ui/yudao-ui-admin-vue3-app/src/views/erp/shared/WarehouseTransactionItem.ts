/** Editable warehouse transaction row shared by sale-out/return and purchase-return forms. */
export interface WarehouseTransactionItem {
  id?: number
  orderItemId?: number
  warehouseId?: number
  fromWarehouseId?: number
  toWarehouseId?: number
  productId?: number
  productName?: string
  productUnitName?: string
  productBarCode?: string
  productPrice?: number
  stockCount?: number | null
  actualCount?: number
  inCount?: number
  outCount?: number
  totalCount?: number
  returnCount?: number
  count?: number
  totalProductPrice?: number
  taxPercent?: number
  taxPrice?: number
  totalPrice?: number
  remark?: string
}

/** Editable parent form fields (do not confuse a new draft with a persisted VO). */
export interface WarehouseTransactionFormData {
  id?: number
  no?: string
  orderId?: number
  orderNo?: string
  supplierId?: number
  customerId?: number
  accountId?: number
  saleUserId?: number
  inTime?: Date | string
  returnTime?: Date | string
  outTime?: Date | string
  remark?: string
  fileUrl: string
  discountPercent: number
  discountPrice: number
  totalPrice: number
  otherPrice: number
  items: WarehouseTransactionItem[]
}

/** A selectable order line has a persisted id, unlike a new transaction line. */
export type WarehouseOrderItem = WarehouseTransactionItem & { id: number }

/** A stock adjustment draft may not have identifiers or a date until submission. */
export interface WarehouseStockFormData {
  id?: number
  no?: string
  supplierId?: number
  customerId?: number
  inTime?: string | Date
  moveTime?: string | Date
  outTime?: string | Date
  checkTime?: string | Date
  remark?: string
  fileUrl: string
  items: WarehouseTransactionItem[]
}

/** A purchase/sale order may be created before a database id or order number exists. */
export interface WarehouseOrderFormData {
  id?: number
  no?: string
  supplierId?: number
  customerId?: number
  accountId?: number
  saleUserId?: number
  orderTime?: Date | string
  remark?: string
  fileUrl: string
  discountPercent: number
  discountPrice: number
  totalPrice: number
  depositPrice: number
  items: WarehouseTransactionItem[]
}
