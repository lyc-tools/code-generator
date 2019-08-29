package io.renren.utils;

import io.renren.entity.ColumnEntity;
import io.renren.entity.TableEntity;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器   工具类
 *
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016年12月19日 下午11:40:24
 */
public class GenUtils {

    public static List<String> getTemplates(){
        List<String> templates = new ArrayList<String>();
        templates.add("template/Bo.java.vm");
        templates.add("template/Vo.java.vm");
        templates.add("template/Do.java.vm");
        templates.add("template/ServiceS.java.vm");
        templates.add("template/Contro.java.vm");
        templates.add("template/Mapper.xml.vm");

        return templates;
    }

    /**
     * 生成代码
     */
    public static void generatorCode(Map<String, String> table,
                                     List<Map<String, String>> columns, ZipOutputStream zip) {
        //配置信息
        Configuration config = getConfig();
        boolean hasBigDecimal = false;
        //表信息
        TableEntity tableEntity = new TableEntity();
        tableEntity.setTableName(table.get("tableName" ));
        tableEntity.setComments(table.get("tableComment" ));
        //表名转换成Java类名
        String className = tableToJava(tableEntity.getTableName(), config.getString("tablePrefix" ) , config.getString("tableSuffix" ));
        tableEntity.setClassName(className);
        tableEntity.setClassname(StringUtils.uncapitalize(className));

        //获取写到硬盘的信息
        boolean writeToDisk = config.getBoolean("writeToDisk", false);
        String writeToDiskBasePath = config.getString("wirteToDiskBasePath","").replace(".", File.separator) + File.separator;

        //获取列信息
        List<ColumnEntity> columsList = getColumnEntityList(columns);
        tableEntity.setColumns(columsList);

        // 获取所有的列
        tableEntity.setAllColumns(columsList.stream().map(ColumnEntity::getColumnName).collect(Collectors.joining(",")));

        // 设置主键,默认用第一个属性
        if(tableEntity.getPk() == null){
            tableEntity.setPk(columsList.get(0));
        }

        // 获取所有的属性
        tableEntity.setAllAttrnames(columsList.stream().map(ColumnEntity::getAttrname).collect(Collectors.joining(",")));

        //设置velocity资源加载器
        Properties prop = new Properties();
        prop.put("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader" );
        Velocity.init(prop);
        String mainPath = config.getString("mainPath" );
        mainPath = StringUtils.isBlank(mainPath) ? "io.renren" : mainPath;
        //封装模板数据
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", tableEntity.getTableName());
        map.put("comments", tableEntity.getComments());
        map.put("allColumns", tableEntity.getAllColumns());
        map.put("allAttrnames", tableEntity.getAllAttrnames());
        map.put("pk", tableEntity.getPk());
        map.put("className", tableEntity.getClassName());
        map.put("classname", tableEntity.getClassname());
        map.put("pathName", tableEntity.getClassname().toLowerCase());
        map.put("columns", tableEntity.getColumns());
        map.put("hasBigDecimal", hasBigDecimal);
        map.put("mainPath", mainPath);
        map.put("package", config.getString("package" ));
        map.put("moduleName", config.getString("moduleName" ));
        map.put("author", config.getString("author" ));
        map.put("email", config.getString("email" ));
        map.put("datetime", DateUtils.format(new Date(), DateUtils.DATE_TIME_PATTERN));
        VelocityContext context = new VelocityContext(map);

        //获取模板列表
        List<String> templates = getTemplates();
        for (String template : templates) {
            //渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, "UTF-8" );
            tpl.merge(context, sw);

            try {
                //添加到zip
                zip.putNextEntry(new ZipEntry(getFileName(template, tableEntity.getClassName(), config.getString("package" ), config.getString("moduleName" ))));
                IOUtils.write(sw.toString(), zip, "UTF-8" );
                if(writeToDisk){
                    writeFileToDisk(sw.toString(), writeToDiskBasePath + getFileName(template, tableEntity.getClassName(), config.getString("package" ), config.getString("moduleName" )));
                }
                IOUtils.closeQuietly(sw);
                zip.closeEntry();
            } catch (IOException e) {
                throw new RRException("渲染模板失败，表名：" + tableEntity.getTableName(), e);
            }
        }
    }


    // 获取column列信息
    public static List<ColumnEntity> getColumnEntityList( List<Map<String, String>> columns ){

        Configuration config = getConfig();
        boolean hasBigDecimal = false;
        List<ColumnEntity> columnsList = new ArrayList<>();

        for(Map<String, String> column : columns){
            ColumnEntity columnEntity = new ColumnEntity();
            columnEntity.setColumnName(column.get("columnName" ));
            columnEntity.setDataType(column.get("dataType" ));
            columnEntity.setComments(column.get("columnComment" ));
            columnEntity.setExtra(column.get("extra" ));

            //列名转换成Java属性名
            String attrName = columnToJava(columnEntity.getColumnName());
            columnEntity.setAttrName(attrName);
            columnEntity.setAttrname(StringUtils.uncapitalize(attrName));

            //列的数据类型，转换成Java类型
            String attrType = config.getString(columnEntity.getDataType().toLowerCase(), "unknowType" );
            columnEntity.setAttrType(attrType);

            if("Boolean".equalsIgnoreCase(attrType)){
                columnEntity.setGet("is");
            }else{
                columnEntity.setGet("get");
            }

            columnsList.add(columnEntity);
        }

        return columnsList;

    }

    public static void writeFileToDisk(String content, String fileName){

        File file = new File(fileName);
        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        try(FileWriter out = new FileWriter(file)){
            out.write(content);
            out.flush();
        }catch (Exception e){
            System.out.println("写出文件失败");
        }
    }

    /**
     * 列名转换成Java属性名
     * 在 _ 后的单词首字母大写,然后去掉 _
     */
    public static String columnToJava(String columnName) {
        return WordUtils.capitalizeFully(columnName, new char[]{'_'}).replace("_", "" );
    }

    /**
     * 表名转换成Java类名
     * 去掉表前缀。在 _ 后的单词首字母大写,然后去掉 _
     */
    public static String tableToJava(String tableName, String tablePrefix, String tableSuffix) {
        if (StringUtils.isNotBlank(tablePrefix)) {
            tableName = tableName.replace(tablePrefix, "" )
                    .replace(tablePrefix.toUpperCase(), "")
                    .replace(tablePrefix.toLowerCase(), "");
        }
        if (StringUtils.isNotBlank(tableSuffix)) {
            tableName = tableName.replace(tableSuffix, "" )
                    .replace(tableSuffix.toUpperCase(), "")
                    .replace(tableSuffix.toLowerCase(), "");
        }
        return columnToJava(tableName);
    }

    /**
     * 获取配置信息
     */
    public static Configuration getConfig() {
        try {
            return new PropertiesConfiguration("generator.properties" );
        } catch (ConfigurationException e) {
            throw new RRException("获取配置文件失败，", e);
        }
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, String className, String packageName, String moduleName) {
        String packagePath = "main" + File.separator + "java" + File.separator;
        if (StringUtils.isNotBlank(packageName)) {
            packagePath += packageName.replace(".", File.separator) + File.separator + moduleName.replace(".",File.separator) + File.separator;
        }

        if (template.contains("Contro.java.vm" )) {
            return packagePath + "controller" + File.separator + className + "C.java";
        }

        if (template.contains("ServiceS.java.vm" )) {
            return packagePath + "service" + File.separator + className + "S.java";
        }

        if (template.contains("Bo.java.vm" )) {
            return packagePath + "model" + File.separator + className + "Bo.java";
        }

        if (template.contains("Vo.java.vm" )) {
            return packagePath + "model" + File.separator + className + "Vo.java";
        }

        if (template.contains("Do.java.vm" )) {
            return packagePath + "model" + File.separator + className + "Do.java";
        }

        if (template.contains("Mapper.xml.vm" )) {
            return packagePath + "map" + File.separator + className + "Mapper.xml";
        }

        return null;
    }
}
