package org.mybatis.paging;

public class PageThreadLocal {
	private static ThreadLocal<Page<?>> threadLocal = new ThreadLocal<Page<?>>();

	private PageThreadLocal() {

	}

	public static Page<?> getThreadLocalPage() {
		return threadLocal.get();
	}

	public static void setThreadLocalPage(Page<?> page) {
		threadLocal.set(page);
	}

	public static void removeThreadLocalPage() {
		threadLocal.remove();
	}
}
