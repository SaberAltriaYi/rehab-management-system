/** A new settlement row has no persisted id; displayed amounts are in the API's units. */
export interface FinancePaymentItem {
  id?: number
  bizId: number
  bizType: number
  bizNo: string
  totalPrice: number
  paidPrice: number
  paymentPrice: number
  remark?: string
}

export interface FinanceReceiptItem {
  id?: number
  bizId: number
  bizType: number
  bizNo: string
  totalPrice: number
  receiptedPrice: number
  receiptPrice: number
  remark?: string
}

/** Drafts are not persisted VOs: identifiers and timestamps are absent on create. */
export interface FinancePaymentDraft {
  id?: number
  no?: string
  supplierId?: number
  accountId?: number
  financeUserId?: number
  paymentTime?: string | Date
  remark?: string
  fileUrl: string
  totalPrice: number
  discountPrice: number
  paymentPrice: number
  items: FinancePaymentItem[]
}

export interface FinanceReceiptDraft {
  id?: number
  no?: string
  customerId?: number
  accountId?: number
  financeUserId?: number
  receiptTime?: string | Date
  remark?: string
  fileUrl: string
  totalPrice: number
  discountPrice: number
  receiptPrice: number
  items: FinanceReceiptItem[]
}
