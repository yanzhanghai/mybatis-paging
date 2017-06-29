# mybatis-paging

极其简单的mybatis 分页
1、配置 mybatis-config.xml


```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
	<settings>
          <setting name="mapUnderscoreToCamelCase" value="true" />
     </settings>
    <plugins>
       <plugin interceptor="org.mybatis.paging.PageInterceptor">
           <property name="databaseType" value="mysql"/>
       </plugin>
    </plugins>
</configuration>
```


2、demo

```
    Page<User> page = new Page<>();
	page.setPageNo(pageNo);
	page.setPageSize(10);
	page.start();
	List<User> l = userMapper.selectByExample(example);
	page.end();
	page.setResults(l);
```

