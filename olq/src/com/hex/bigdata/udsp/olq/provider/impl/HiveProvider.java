package com.hex.bigdata.udsp.olq.provider.impl;

import com.hex.bigdata.udsp.common.constant.Status;
import com.hex.bigdata.udsp.common.constant.StatusCode;
import com.hex.bigdata.udsp.common.provider.model.Datasource;
import com.hex.bigdata.udsp.common.provider.model.Page;
import com.hex.bigdata.udsp.common.util.JSONUtil;
import com.hex.bigdata.udsp.olq.model.OLQQuerySql;
import com.hex.bigdata.udsp.olq.provider.Provider;
import com.hex.bigdata.udsp.olq.provider.impl.model.HiveDatasource;
import com.hex.bigdata.udsp.olq.provider.model.OLQRequest;
import com.hex.bigdata.udsp.olq.provider.model.OLQResponse;
import com.hex.bigdata.udsp.olq.provider.model.OLQResponseFetch;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * Created by junjiem on 2017-2-15.
 */
@Component("com.hex.bigdata.udsp.olq.provider.impl.HiveProvider")
public class HiveProvider implements Provider {
    private static Logger logger = LogManager.getLogger(HiveProvider.class);
    private static Map<String, BasicDataSource> dataSourcePool;

    private synchronized BasicDataSource getDataSource(HiveDatasource hiveDatasource) {
        String dsId = hiveDatasource.getId();
        if (dataSourcePool == null) {
            dataSourcePool = new HashMap<String, BasicDataSource>();
        }
        BasicDataSource dataSource = dataSourcePool.get(dsId);
        if (dataSource == null) {
            dataSource = new BasicDataSource();

            //Class.forName(hiveDatasource.getDriverClass());
            if (StringUtils.isNotBlank(hiveDatasource.getDriverClass()))
                dataSource.setDriverClassName(hiveDatasource.getDriverClass());
            if (StringUtils.isNotBlank(hiveDatasource.getJdbcUrl()))
                dataSource.setUrl(hiveDatasource.getJdbcUrl());
            if (StringUtils.isNotBlank(hiveDatasource.getUsername()))
                dataSource.setUsername(hiveDatasource.getUsername());
            if (StringUtils.isNotBlank(hiveDatasource.getPassword()))
                dataSource.setPassword(hiveDatasource.getPassword());
            if (StringUtils.isNotBlank(hiveDatasource.getInitialSize()))
                dataSource.setInitialSize(Integer.valueOf(hiveDatasource.getInitialSize()));// 数据库初始化时，创建的连接个数
            if (StringUtils.isNotBlank(hiveDatasource.getMinIdle()))
                dataSource.setMinIdle(Integer.valueOf(hiveDatasource.getMinIdle()));// 最小空闲连接数
            if (StringUtils.isNotBlank(hiveDatasource.getMaxIdle()))
                dataSource.setMaxIdle(Integer.valueOf(hiveDatasource.getMaxIdle()));// 数据库最大连接数
            if (StringUtils.isNotBlank(hiveDatasource.getMaxActive()))
                dataSource.setMaxActive(Integer.valueOf(hiveDatasource.getMaxActive()));// 设置最大并发数
            if (StringUtils.isNotBlank(hiveDatasource.getMaxWait()))
                dataSource.setMaxWait(Integer.valueOf(hiveDatasource.getMaxWait()));// 最长等待时间，单位毫秒
            if (StringUtils.isNotBlank(hiveDatasource.getValidationQuery()))
                dataSource.setValidationQuery(hiveDatasource.getValidationQuery()); // 验证链接的SQL语句，必须能返回一行及以上数据
            if (StringUtils.isNotBlank(hiveDatasource.getValidationQueryTimeout()))
                dataSource.setValidationQueryTimeout(Integer.valueOf(hiveDatasource.getValidationQueryTimeout())); // 自动验证连接的时间
            if (StringUtils.isNotBlank(hiveDatasource.getTimeBetweenEvictionRunsMillis()))
                dataSource.setTimeBetweenEvictionRunsMillis(Integer.valueOf(hiveDatasource.getTimeBetweenEvictionRunsMillis())); // N毫秒检测一次是否有死掉的线程
            if (StringUtils.isNotBlank(hiveDatasource.getMinEvictableIdleTimeMillis()))
                dataSource.setMinEvictableIdleTimeMillis(Integer.valueOf(hiveDatasource.getMinEvictableIdleTimeMillis()));// 空闲连接N毫秒中后释放
            if (StringUtils.isNotBlank(hiveDatasource.getTestWhileIdle()))
                dataSource.setTestWhileIdle(Boolean.valueOf(hiveDatasource.getTestWhileIdle()));
            if (StringUtils.isNotBlank(hiveDatasource.getTestOnBorrow()))
                dataSource.setTestOnBorrow(Boolean.valueOf(hiveDatasource.getTestOnBorrow()));
            if (StringUtils.isNotBlank(hiveDatasource.getTestOnReturn()))
                dataSource.setTestOnReturn(Boolean.valueOf(hiveDatasource.getTestOnReturn()));

            dataSourcePool.put(dsId, dataSource);
        }
        return dataSource;
    }

    private Connection getConnection(HiveDatasource hiveDatasource) throws SQLException {
        Connection conn = null;
        BasicDataSource dataSource = getDataSource(hiveDatasource);
        if (dataSource != null) {
            conn = dataSource.getConnection();
        }
        return conn;
    }

    public void init(Datasource datasource) {
        try {
            HiveDatasource hiveDatasource = new HiveDatasource(datasource.getPropertyMap());
            getConnection(hiveDatasource);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public OLQResponse execute(OLQRequest request) {
        logger.debug("request=" + JSONUtil.parseObj2JSON(request));
        long bef = System.currentTimeMillis();

        OLQResponse response = new OLQResponse();
        response.setRequest(request);

        Datasource datasource = request.getDatasource();

        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        Map<String, String> map = null;
        int count = 0;
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        HiveDatasource hiveDatasource = new HiveDatasource(datasource.getPropertyMap());
        try {
            conn = getConnection(hiveDatasource);
            stmt = conn.createStatement();
            //获取查询信息
            OLQQuerySql olqQuerySql = request.getOlqQuerySql();
            if (olqQuerySql.getPage() == null){
                rs = stmt.executeQuery(olqQuerySql.getOriginalSql());
            }else {
                rs = stmt.executeQuery(olqQuerySql.getPageSql());
            }
            rs.setFetchSize(1000);
            int max_num_size = hiveDatasource.getMaxNum();
            ResultSetMetaData rsmd = rs.getMetaData();
            response.setMetadata(rsmd);
            int columnCount = rsmd.getColumnCount();
            while (rs.next()) {
                map = new LinkedHashMap<String, String>();
                for (int i = 1; i <= columnCount; i++) {
                    //map.put(rsmd.getColumnName(i), rs.getString(i));
                    String columnName = rsmd.getColumnLabel(i);
                    int index = columnName.indexOf(".");
                    columnName = index == 1 ? columnName : columnName.substring(index + 1, columnName.length());
                    map.put(columnName, rs.getString(i));
                }
                list.add(map);
                count++;
                if (count >= max_num_size) {
                    break;
                }
            }
            //查询的记录数总数
            String totalSql = olqQuerySql.getTotalSql();
            if (StringUtils.isNotBlank(totalSql)){
                rs = stmt.executeQuery(totalSql);
                int totalCount = 0;
                if (rs.next()){
                    totalCount = rs.getInt(1);
                }
                //设置总记录数
                olqQuerySql.getPage().setTotalCount(totalCount);
                response.setPage(olqQuerySql.getPage());
            }
            response.setStatus(Status.SUCCESS);
            response.setStatusCode(StatusCode.SUCCESS);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(Status.DEFEAT);
            response.setStatusCode(StatusCode.DEFEAT);
            response.setMessage(e.getMessage());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        long now = System.currentTimeMillis();
        long consumeTime = now - bef;

        response.setRecords(list);
        response.setConsumeTime(consumeTime);
        response.setTotalCount(count);

        logger.debug("consumeTime=" + response.getConsumeTime() + " recordsSize=" + response.getRecords().size());
        return response;
    }

    public synchronized void close(Datasource datasource) {
        BasicDataSource dataSource = dataSourcePool.remove(datasource.getId());
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean testDatasource(Datasource datasource) {
        boolean canConnection = true;
        Connection connection = null;
        try {
            HiveDatasource hiveDatasource = new HiveDatasource(datasource.getPropertyMap());
            connection = getConnection(hiveDatasource);
            if (connection == null) {
                canConnection = false;
            } else {
                canConnection = true;
            }
        } catch (Exception e) {
            canConnection = false;
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        return canConnection;
    }

    @Override
    public OLQResponseFetch executeFetch(OLQRequest request) {
        logger.debug("request=" + JSONUtil.parseObj2JSON(request));
        long bef = System.currentTimeMillis();

        OLQResponseFetch response = new OLQResponseFetch();
        response.setRequest(request);

        Datasource datasource = request.getDatasource();

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        HiveDatasource hiveDatasource = new HiveDatasource(datasource.getPropertyMap());
        try {
            conn = getConnection(hiveDatasource);
            stmt = conn.createStatement();
            OLQQuerySql olqQuerySql = request.getOlqQuerySql();
            rs = stmt.executeQuery(olqQuerySql.getOriginalSql());
            rs.setFetchSize(1000);
            response.setStatus(Status.SUCCESS);
            response.setStatusCode(StatusCode.SUCCESS);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(Status.DEFEAT);
            response.setStatusCode(StatusCode.DEFEAT);
            response.setMessage(e.getMessage());
        }
        response.setConnection(conn);
        response.setStatement(stmt);
        response.setResultSet(rs);

        long now = System.currentTimeMillis();
        long consumeTime = now - bef;
        response.setConsumeTime(consumeTime);

        logger.debug("consumeTime=" + response.getConsumeTime());
        return response;
    }

    @Override
    public OLQQuerySql getPageSql(String sql, Page page) {
        OLQQuerySql olqQuerySql = new OLQQuerySql(sql);
        if (page == null) {
            return olqQuerySql;
        }
        //分页sql组装
        int pageSize = page.getPageSize();
        int pageIndex = page.getPageIndex();
        pageIndex = pageIndex == 0 ? 1 : pageIndex;
        Integer startRow = (pageIndex - 1) * pageSize;
        Integer endRow = pageSize * pageIndex;
        StringBuffer pageSqlBuffer = new StringBuffer("select * from (select row_number() over (order by 1) as rownum,table.*  FROM (");
        pageSqlBuffer.append(sql);
        pageSqlBuffer.append(" )table)t where t.rownum between ");
        pageSqlBuffer.append(startRow);
        pageSqlBuffer.append(" and  ");
        pageSqlBuffer.append(endRow);
        olqQuerySql.setPageSql(pageSqlBuffer.toString());
        //总记录数查询SQL组装
        StringBuffer totalSqlBuffer = new StringBuffer("select count(*) from (");
        totalSqlBuffer.append(sql);
        totalSqlBuffer.append(")t");
        olqQuerySql.setTotalSql(totalSqlBuffer.toString());
        //page设置
        olqQuerySql.setPage(page);
        return olqQuerySql;
    }

    public static void main(String[] args) {
        String sql = "SELECT * FROM OMDATA.S01_SJYMB JYM";
        Page page = new Page();
        page.setPageIndex(2);
        page.setPageSize(20);
        HiveProvider hiveProvider = new HiveProvider();
        System.out.println(hiveProvider.getPageSql(sql, page));
    }
}
