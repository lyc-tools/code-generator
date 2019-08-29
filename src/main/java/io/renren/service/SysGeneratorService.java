package io.renren.service;

import io.renren.dao.SysGeneratorDaoMysql;
import io.renren.dao.SysGeneratorDaoOra;
import io.renren.utils.GenUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016年12月19日 下午3:33:38
 */
@Service
public class SysGeneratorService {
	@Autowired
	private SysGeneratorDaoMysql sysGeneratorDaoMysql;
	@Autowired
	private SysGeneratorDaoOra sysGeneratorDaoOra;
	@Value("${data.dialect}")
	private String dialect;

	public List<Map<String, Object>> queryList(Map<String, Object> map) {
		return isOracle() ? sysGeneratorDaoOra.queryList(map) : sysGeneratorDaoMysql.queryList(map);
	}

	public int queryTotal(Map<String, Object> map) {
		return isOracle() ? sysGeneratorDaoOra.queryTotal(map) : sysGeneratorDaoMysql.queryTotal(map);
	}

	public Map<String, String> queryTable(String tableName) {
		return isOracle() ? sysGeneratorDaoOra.queryTable(tableName) : sysGeneratorDaoMysql.queryTable(tableName);
	}

	public List<Map<String, String>> queryColumns(String tableName) {
		return isOracle() ? sysGeneratorDaoOra.queryColumns(tableName) : sysGeneratorDaoMysql.queryColumns(tableName);
	}

	public byte[] generatorCode(String[] tableNames) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(outputStream);

		for(String tableName : tableNames){
			//查询表信息
			Map<String, String> table = queryTable(tableName);
			//查询列信息
			List<Map<String, String>> columns = queryColumns(tableName);
			//生成代码
			GenUtils.generatorCode(table, columns, zip);
		}
		IOUtils.closeQuietly(zip);
		return outputStream.toByteArray();
	}

	public boolean isOracle(){
		return "oracle".equalsIgnoreCase(dialect);


	}
}
