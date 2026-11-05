/**
 * Files on customers, orders and invoices (spec 2026-11-05). Owns {@code attachment}; stores bytes through
 * {@code files.StorageService}; reads {@code sales_order}, {@code invoice} and {@code delivery_order} through SQL.
 */
package ph.thecoffeejunkie.crm.attachments;
