package org.mybatis.paging;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分页拦截器
 * 
 * @author yanzhanghai
 * 
 */
@Intercepts({ @Signature(method = "prepare", type = StatementHandler.class, args = { Connection.class }) })
public class PageInterceptor implements Interceptor {
	private final static Logger LOGGER = LoggerFactory
			.getLogger(PageInterceptor.class);
	private String databaseType;// 数据库类型，不同的数据库有不同的分页方法

	/**
	 * 拦截后要执行的方法
	 */
	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		Page<?> page = PageThreadLocal.getThreadLocalPage();
		if (page == null) {
			return invocation.proceed();
		}
		PageThreadLocal.removeThreadLocalPage();
		RoutingStatementHandler handler = (RoutingStatementHandler) invocation
				.getTarget();
		// 通过反射获取到当前RoutingStatementHandler对象的delegate属性
		StatementHandler delegate = (StatementHandler) ReflectUtil
				.getFieldValue(handler, "delegate");
		// 获取到当前StatementHandler的
		// RoutingStatementHandler实现的所有StatementHandler接口方法里面都是调用的delegate对应的方法。
		BoundSql boundSql = delegate.getBoundSql();
		// 拿到当前绑定Sql的参数对象，就是我们在调用对应的Mapper映射语句时所传入的参数对象
		//移除  兼容参数为空 查询所有记录场景
		/*if (boundSql.getParameterObject() == null) {
			return invocation.proceed();
		}*/

		// 通过反射获取delegate父类BaseStatementHandler的mappedStatement属性
		MappedStatement mappedStatement = (MappedStatement) ReflectUtil
				.getFieldValue(delegate, "mappedStatement");

		//判断是否需要总条目数，需要时查询总条目数（页面展示时一般需要，接口调用纯数据分页时不用）
		if( page.isNeedTotalRecord() ){
			// 给当前的page参数对象设置总记录数
			setTotalRecord(page, mappedStatement, boundSql);
		}
		appendPageParameter(page, boundSql, mappedStatement);
		return invocation.proceed();
	}

	private void appendPageParameter(Page<?> page, BoundSql boundSql,
			MappedStatement mappedStatement) {
		List<ParameterMapping> parameterMappings = boundSql
				.getParameterMappings();
		if (parameterMappings == null || parameterMappings.size() < 1) {
			parameterMappings = new ArrayList<ParameterMapping>();
			ReflectUtil.setFieldValue(boundSql, "parameterMappings",
					parameterMappings);
		}
		String sql = boundSql.getSql();
		String pageSql = null;
		if ("mysql".equals(this.databaseType)) {
			pageSql = this.getMysqlPageSql(sql);
			setMysqlParameters(page, boundSql, mappedStatement,
					parameterMappings);
		} else {
			pageSql = getOraclePageSql(sql);
			setOracleParameters(page, boundSql, mappedStatement,
					parameterMappings);
		}
		ReflectUtil.setFieldValue(boundSql, "sql", pageSql);
	}

	private void setMysqlParameters(Page<?> page, BoundSql boundSql,
			MappedStatement mappedStatement,
			List<ParameterMapping> parameterMappings) {
		parameterMappings.add(new ParameterMapping.Builder(mappedStatement
				.getConfiguration(), "pageStart", Integer.class).build());
		boundSql.setAdditionalParameter("pageStart", (page.getPageNo() - 1)
				* page.getPageSize());
		parameterMappings.add(new ParameterMapping.Builder(mappedStatement
				.getConfiguration(), "limit", Integer.class).build());
		boundSql.setAdditionalParameter("limit", page.getPageSize());
	}

	private void setOracleParameters(Page<?> page, BoundSql boundSql,
			MappedStatement mappedStatement,
			List<ParameterMapping> parameterMappings) {

		parameterMappings.add(new ParameterMapping.Builder(mappedStatement
				.getConfiguration(), "pageEnd", Integer.class).build());
		parameterMappings.add(new ParameterMapping.Builder(mappedStatement
				.getConfiguration(), "pageStart", Integer.class).build());
		boundSql.setAdditionalParameter("pageStart", (page.getPageNo() - 1)
				* page.getPageSize());
		boundSql.setAdditionalParameter("pageEnd",
				page.getPageNo() * page.getPageSize());
	}

	/**
	 * 拦截器对应的封装原始对象的方法
	 */
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	/**
	 * 设置注册拦截器时设定的属性
	 */
	public void setProperties(Properties properties) {
		this.databaseType = properties.getProperty("databaseType");
	}

	/**
	 * 获取Mysql数据库的分页查询语句
	 * 
	 * @param page
	 *            分页对象
	 * @param new StringBubber(sql) 包含原sql语句的StringBuffer对象
	 * @return Mysql数据库分页语句
	 */
	private String getMysqlPageSql(String sql) {
		return new StringBuffer(sql).append(" limit ?,?").toString();

	}

	/**
	 * 获取Oracle数据库的分页查询语句
	 * 
	 * @param page
	 *            分页对象
	 * @param sqlBuffer
	 *            包含原sql语句的StringBuffer对象
	 * @return Oracle数据库的分页查询语句
	 */
	private String getOraclePageSql(String sql) {
		return new StringBuffer(sql)
				.insert(0, "select * from (select u.*, rownum r from (")
				.append(" )u where rownum <= ?) where r > ?").toString();
	}

	/**
	 * 给当前的参数对象page设置总记录数
	 * 
	 * @param page
	 * @param mappedStatement
	 * @param boundSql
	 */
	private void setTotalRecord(Page<?> page, MappedStatement mappedStatement,
			BoundSql boundSql) {
		String originSql = boundSql.getSql();
		String countSql = this.getCountSql(originSql);
		// 通过BoundSql获取对应的参数映射
		ReflectUtil.setFieldValue(boundSql, "sql", countSql);
		ParameterHandler parameterHandler = new DefaultParameterHandler(
				mappedStatement, boundSql.getParameterObject(), boundSql);
		// 通过connection建立一个countSql对应的PreparedStatement对象。
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Connection con = null;
		try {
			con = mappedStatement.getConfiguration().getEnvironment()
					.getDataSource().getConnection();
			pstmt = con.prepareStatement(countSql);
			// 通过parameterHandler给PreparedStatement对象设置参数
			parameterHandler.setParameters(pstmt);
			// 之后就是执行获取总记录数的Sql语句和获取结果了。
			rs = pstmt.executeQuery();
			if (rs.next()) {
				int totalRecord = rs.getInt(1);
				// 给当前的参数page对象设置总记录数
				page.setTotalRecord(totalRecord);
			}
			
			//当从连接池取到的连接的 autoCommit = false 时，需要手动提交，以便取得及时更新的数据
			//分页查询记录条目数未及时更新问题修复
			if(con.getAutoCommit() == false) {
			    con.commit();
			}
		} catch (SQLException e) {
			LOGGER.error("set total count error, sql is: " + countSql, e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
				if (con != null) {
					con.close();
				}
			} catch (SQLException e) {
				LOGGER.error("set total count error", e);
			}
		}
		ReflectUtil.setFieldValue(boundSql, "sql", originSql);
	}

	/**
	 * 根据原Sql语句获取对应的查询总记录数的Sql语句
	 * 
	 * @param sql
	 * @return
	 */
	private String getCountSql(String sql) {
		int index = sql.toLowerCase().indexOf("from");
		return "select count(*) " + sql.substring(index);
	}

	/**
	 * 利用反射进行操作的一个工具类
	 * 
	 */
	private static class ReflectUtil {
		/**
		 * 利用反射获取指定对象的指定属性
		 * 
		 * @param obj
		 *            目标对象
		 * @param fieldName
		 *            目标属性
		 * @return 目标属性的值
		 */
		public static Object getFieldValue(Object obj, String fieldName) {
			Object result = null;
			Field field = ReflectUtil.getField(obj, fieldName);
			if (field != null) {
				field.setAccessible(true);
				try {
					result = field.get(obj);
				} catch (IllegalArgumentException e) {
					LOGGER.error("", e);
				} catch (IllegalAccessException e) {
					LOGGER.error("", e);
				}
			}
			return result;
		}

		/**
		 * 利用反射获取指定对象里面的指定属性
		 * 
		 * @param obj
		 *            目标对象
		 * @param fieldName
		 *            目标属性
		 * @return 目标字段
		 */
		private static Field getField(Object obj, String fieldName) {
			Field field = null;
			for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz
					.getSuperclass()) {
				try {
					field = clazz.getDeclaredField(fieldName);
					break;
				} catch (NoSuchFieldException e) {
					// 这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
				}
			}
			return field;
		}

		/**
		 * 利用反射设置指定对象的指定属性为指定的值
		 * 
		 * @param obj
		 *            目标对象
		 * @param fieldName
		 *            目标属性
		 * @param fieldValue
		 *            目标值
		 */
		public static void setFieldValue(Object obj, String fieldName,
				Object fieldValue) {
			Field field = ReflectUtil.getField(obj, fieldName);
			if (field != null) {
				try {
					field.setAccessible(true);
					field.set(obj, fieldValue);
				} catch (IllegalArgumentException e) {
					LOGGER.error("", e);
				} catch (IllegalAccessException e) {
					LOGGER.error("", e);
				}
			}
		}
	}

}