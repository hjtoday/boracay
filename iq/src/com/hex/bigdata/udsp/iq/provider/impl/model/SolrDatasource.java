package com.hex.bigdata.udsp.iq.provider.impl.model;

import com.hex.bigdata.udsp.common.provider.model.Datasource;
import com.hex.bigdata.udsp.common.provider.model.Property;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Solr的数据源配置
 */
public class SolrDatasource extends Datasource {
    private String solrServers;
    private int maxNum = 65535;

    public SolrDatasource(List<Property> properties) {
        super(properties);
    }

    public SolrDatasource(Map<String, Property> propertieMap) {
        super(propertieMap);
    }

    public String getSolrServers() {
        String value = getProperty("solr.servers").getValue();
        if (StringUtils.isBlank(value))
            throw new IllegalArgumentException("solr.servers不能为空");
        return value;
    }

    public int getMaxNum() {
        String value = getProperty("solr.max.data.size").getValue();
        if (StringUtils.isNotBlank(value)) {
            try {
                maxNum = Integer.valueOf(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("solr.max.data.size为一整数");
            }
        }
        return maxNum;
    }
}
