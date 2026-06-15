---
source: MinDoc
project_name: 开发规范文档
source_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1f3u19ojreo55
normalized_url: https://docs.cvte.com/docs/plateform_dev_rule/plateform_dev_rule-1f3u19ojreo55
url_hash: 7ba780c0c99a
document_key: 开发规范文档_7ba780c0c99a
doc_id: plateform_dev_rule-1f3u19ojreo55
title: AIGC做数据加工
md_hash: 6f42a6bf3ed41a42
version: 1697788128
image_count: 0
crawled_at: 2026-06-11 17:53:02
---

# AIGC做数据加工

## 来自你的消息:


请根据以下excel格式数据


职位名称 职位HID 旧岗位HID 新岗位HID 部门HID 部门OID
总监 854855155c77426fb3713cd66bb5820d 97424c621fc947078a3fdee7437bcf2e 24dfb4d4b55e4f8995d8179650c3da3b 480a9d926210406594386ca610a37ec4 f9e7e8416372411f912c5ef659829d32


编写程序如下


根据每一行部门OID查询对具体sql的org_id字段为查询条件如下：


select id,parent_id from sys_org_relation sor where org_id = ‘f9e7e8416372411f912c5ef659829d32’;


如果parent_id不为-1，则继续递归查询，将所有的id存起来，直到parent_id为-1，递归结束。


将所有的id存起来，然后根据id查询对应的sys_org_relation_id，而role_rel_id则对应的是旧岗位HID，具体sql如下：


select id as role_unit_id from sys_role_user_unit where role_rel_type = ‘job’ and role_rel_id = ‘cb9d91767c7b46f7880eb96527cffee8’ and sys_org_relation_id = ‘上述查询的id’;


如果上述的sql查询到对应的数据，则生成对应的sql，role_rel_id为新岗位的id, id位上述的：role_unit_id：


update sys_role_user_unit set role_rel_id = ‘24dfb4d4b55e4f8995d8179650c3da3b’ where id = ‘role_unit_id’;


要求代码清晰，用java实现


## 来自 AIGC 的消息:


以下是用Java实现的代码：


```
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UpdateRoleUserUnit {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/database_name";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        String filePath = "path_to_excel_file";
        List<String[]> data = readExcel(filePath);
        for (String[] row : data) {
            String departmentOID = row[5];
            List<String> orgIds = getOrgIds(departmentOID);
            for (String orgId : orgIds) {
                List<String> roleUnitIds = getRoleUnitIds(orgId, row[2]);
                for (String roleUnitId : roleUnitIds) {
                    updateRoleUserUnit(roleUnitId, row[3]);
                }
            }
        }
    }

    private static List<String[]> readExcel(String filePath) {
        // 读取Excel文件并解析数据
        // 返回一个包含每一行数据的列表
    }

    private static List<String> getOrgIds(String departmentOID) {
        List<String> orgIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id, parent_id from sys_org_relation where org_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, departmentOID);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String parentId = resultSet.getString("parent_id");
                orgIds.add(id);
                if (!parentId.equals("-1")) {
                    orgIds.addAll(getOrgIds(parentId));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orgIds;
    }

    private static List<String> getRoleUnitIds(String orgId, String oldJobHID) {
        List<String> roleUnitIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id from sys_role_user_unit where role_rel_type = 'job' and role_rel_id = ? and sys_org_relation_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, oldJobHID);
            statement.setString(2, orgId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String roleUnitId = resultSet.getString("id");
                roleUnitIds.add(roleUnitId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleUnitIds;
    }

    private static void updateRoleUserUnit(String roleUnitId, String newJobHID) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "update sys_role_user_unit set role_rel_id = ? where id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, newJobHID);
            statement.setString(2, roleUnitId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```


请注意，上述代码中的JDBC连接URL、用户名和密码需要根据实际情况进行修改。另外，readExcel()方法需要根据实际情况实现Excel文件的读取逻辑。


## 来自你的消息:


请用easy poi实现excel数据读取的逻辑代码


## 来自 AIGC 的消息:


使用EasyPoi实现Excel数据读取的逻辑代码如下：


```
import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ImportParams;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.util.List;

public class ExcelReader {
    public static void main(String[] args) {
        String filePath = "path_to_excel_file";
        List<ExcelData> data = readExcel(filePath);
        // 处理读取到的数据
    }

    private static List<ExcelData> readExcel(String filePath) {
        ImportParams importParams = new ImportParams();
        importParams.setHeadRows(1); // 设置表头所占行数，默认为1
        importParams.setTitleRows(0); // 设置标题所占行数，默认为0
        importParams.setStartSheetIndex(0); // 设置读取的起始Sheet索引，默认为0

        File file = new File(filePath);
        Workbook workbook = ExcelImportUtil.importExcel(file, ExcelData.class, importParams);
        List<ExcelData> data = ExcelImportUtil.importExcel(workbook, ExcelData.class, importParams);
        return data;
    }
}
```


在上述代码中，ExcelData是一个自定义的Java类，用于存储Excel中的数据。你可以根据实际情况定义ExcelData类的属性，并在其中添加对应的getter和setter方法。


请确保在运行代码之前，已经将EasyPoi相关的依赖添加到项目中。你可以在项目的pom.xml文件中添加以下依赖：


```
<dependency>
    <groupId>cn.afterturn</groupId>
    <artifactId>easypoi-base</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>cn.afterturn</groupId>
    <artifactId>easypoi-annotation</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>cn.afterturn</groupId>
    <artifactId>easypoi-excel</artifactId>
    <version>3.0.0</version>
</dependency>
```


请注意，上述代码中的ExcelData类需要根据实际情况进行定义和修改。另外，需要根据实际情况修改Excel文件的路径。


## 来自你的消息:


将上述代码综合在一起，并创建对应的java自定义类


## 来自 AIGC 的消息:


以下是综合了Excel数据读取逻辑和其他相关代码的完整示例：


```
import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ImportParams;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelDataProcessor {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/database_name";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        String filePath = "path_to_excel_file";
        List<ExcelData> data = readExcel(filePath);
        for (ExcelData row : data) {
            String departmentOID = row.getDepartmentOID();
            List<String> orgIds = getOrgIds(departmentOID);
            for (String orgId : orgIds) {
                List<String> roleUnitIds = getRoleUnitIds(orgId, row.getOldJobHID());
                for (String roleUnitId : roleUnitIds) {
                    updateRoleUserUnit(roleUnitId, row.getNewJobHID());
                }
            }
        }
    }

    private static List<ExcelData> readExcel(String filePath) {
        ImportParams importParams = new ImportParams();
        importParams.setHeadRows(1);
        importParams.setTitleRows(0);
        importParams.setStartSheetIndex(0);

        File file = new File(filePath);
        Workbook workbook = ExcelImportUtil.importExcel(file, ExcelData.class, importParams);
        List<ExcelData> data = ExcelImportUtil.importExcel(workbook, ExcelData.class, importParams);
        return data;
    }

    private static List<String> getOrgIds(String departmentOID) {
        List<String> orgIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id, parent_id from sys_org_relation where org_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, departmentOID);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String parentId = resultSet.getString("parent_id");
                orgIds.add(id);
                if (!parentId.equals("-1")) {
                    orgIds.addAll(getOrgIds(parentId));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orgIds;
    }

    private static List<String> getRoleUnitIds(String orgId, String oldJobHID) {
        List<String> roleUnitIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id from sys_role_user_unit where role_rel_type = 'job' and role_rel_id = ? and sys_org_relation_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, oldJobHID);
            statement.setString(2, orgId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String roleUnitId = resultSet.getString("id");
                roleUnitIds.add(roleUnitId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleUnitIds;
    }

    private static void updateRoleUserUnit(String roleUnitId, String newJobHID) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "update sys_role_user_unit set role_rel_id = ? where id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, newJobHID);
            statement.setString(2, roleUnitId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```


在上述代码中，`ExcelData`是一个自定义的Java类，用于存储Excel中的数据。请根据实际情况定义`ExcelData`类的属性，并在其中添加对应的getter和setter方法。


请确保在运行代码之前，已经将EasyPoi相关的依赖添加到项目中，并将Excel文件路径替换为实际的路径。另外，需要根据实际情况修改数据库的JDBC连接URL、用户名和密码。


希望这可以帮助到你！


## 来自你的消息:


把excelDATa类也完整呈现出来


## 来自 AIGC 的消息:


以下是完整的ExcelData类的示例：


```
public class ExcelData {
    private String jobTitle;
    private String jobHID;
    private String oldJobHID;
    private String newJobHID;
    private String departmentHID;
    private String departmentOID;

    // 添加对应的getter和setter方法

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getJobHID() {
        return jobHID;
    }

    public void setJobHID(String jobHID) {
        this.jobHID = jobHID;
    }

    public String getOldJobHID() {
        return oldJobHID;
    }

    public void setOldJobHID(String oldJobHID) {
        this.oldJobHID = oldJobHID;
    }

    public String getNewJobHID() {
        return newJobHID;
    }

    public void setNewJobHID(String newJobHID) {
        this.newJobHID = newJobHID;
    }

    public String getDepartmentHID() {
        return departmentHID;
    }

    public void setDepartmentHID(String departmentHID) {
        this.departmentHID = departmentHID;
    }

    public String getDepartmentOID() {
        return departmentOID;
    }

    public void setDepartmentOID(String departmentOID) {
        this.departmentOID = departmentOID;
    }
}
```


请根据实际情况修改ExcelData类的属性，确保与Excel表格中的列对应。如果Excel表格中有其他列，你可以根据需要在ExcelData类中添加相应的属性，并在其中添加对应的getter和setter方法。


希望这可以满足你的需求！


## 来自你的消息:


对象实体中请添加poi的属性注解


## 来自 AIGC 的消息:


在ExcelData类中，你可以使用EasyPoi提供的注解来标识属性与Excel表格中的列对应关系。以下是添加了EasyPoi注解的ExcelData类的示例：


```
import cn.afterturn.easypoi.excel.annotation.Excel;

public class ExcelData {
    @Excel(name = "Job Title")
    private String jobTitle;

    @Excel(name = "Job HID")
    private String jobHID;

    @Excel(name = "Old Job HID")
    private String oldJobHID;

    @Excel(name = "New Job HID")
    private String newJobHID;

    @Excel(name = "Department HID")
    private String departmentHID;

    @Excel(name = "Department OID")
    private String departmentOID;

    // 添加对应的getter和setter方法

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getJobHID() {
        return jobHID;
    }

    public void setJobHID(String jobHID) {
        this.jobHID = jobHID;
    }

    public String getOldJobHID() {
        return oldJobHID;
    }

    public void setOldJobHID(String oldJobHID) {
        this.oldJobHID = oldJobHID;
    }

    public String getNewJobHID() {
        return newJobHID;
    }

    public void setNewJobHID(String newJobHID) {
        this.newJobHID = newJobHID;
    }

    public String getDepartmentHID() {
        return departmentHID;
    }

    public void setDepartmentHID(String departmentHID) {
        this.departmentHID = departmentHID;
    }

    public String getDepartmentOID() {
        return departmentOID;
    }

    public void setDepartmentOID(String departmentOID) {
        this.departmentOID = departmentOID;
    }
}
```


在上述代码中，通过使用`[@Excel](https://github.com/Excel)`注解，可以指定属性与Excel表格中的列的对应关系。`name`属性用于指定Excel表格中列的标题。你可以根据实际情况修改注解的属性值。


希望这样可以满足你的需求！


## 来自你的消息:


刚才的代码逻辑有错，请重新根据边边的逻辑重新输出对应的代码


请根据以下excel格式数据


职位名称 职位HID 旧岗位HID 新岗位HID 部门HID 部门OID
总监 854855155c77426fb3713cd66bb5820d 97424c621fc947078a3fdee7437bcf2e 24dfb4d4b55e4f8995d8179650c3da3b 480a9d926210406594386ca610a37ec4 f9e7e8416372411f912c5ef659829d32


编写程序如下


根据每一行部门OID查询对具体sql的org_id字段为查询条件如下：


select id from sys_org_relation sor where org_id = ‘f9e7e8416372411f912c5ef659829d32’;


根据上述的id，进行如下sql的递归查询：


select id,parent_id from sys_org_relation sor where id = ‘id’;


如果上述sql的parent_id不为-1，则继续递归查询，将所有的id存起来，直到parent_id为-1，递归结束。


将所有的id存起来，然后根据id查询对应的sys_org_relation_id，而role_rel_id则对应的是旧岗位HID，具体sql如下：


select id as role_unit_id from sys_role_user_unit where role_rel_type = ‘job’ and role_rel_id = ‘cb9d91767c7b46f7880eb96527cffee8’ and sys_org_relation_id = ‘上述查询的id’;


如果上述的sql查询到对应的数据，则生成对应的sql，role_rel_id为新岗位的id, id位上述的：role_unit_id：


update sys_role_user_unit set role_rel_id = ‘24dfb4d4b55e4f8995d8179650c3da3b’ where id = ‘role_unit_id’;


要求代码清晰，用java实现


## 来自 AIGC 的消息:


根据你提供的逻辑，以下是重新编写的代码：


```
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelDataProcessor {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/database_name";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        String departmentOID = "f9e7e8416372411f912c5ef659829d32";
        String oldJobHID = "97424c621fc947078a3fdee7437bcf2e";
        String newJobHID = "24dfb4d4b55e4f8995d8179650c3da3b";
        List<String> orgIds = getOrgIds(departmentOID);
        for (String orgId : orgIds) {
            List<String> roleUnitIds = getRoleUnitIds(orgId, oldJobHID);
            for (String roleUnitId : roleUnitIds) {
                updateRoleUserUnit(roleUnitId, newJobHID);
            }
        }
    }

    private static List<String> getOrgIds(String departmentOID) {
        List<String> orgIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id, parent_id from sys_org_relation where org_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, departmentOID);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String parentId = resultSet.getString("parent_id");
                orgIds.add(id);
                if (!parentId.equals("-1")) {
                    orgIds.addAll(getOrgIds(parentId));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orgIds;
    }

    private static List<String> getRoleUnitIds(String orgId, String oldJobHID) {
        List<String> roleUnitIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id from sys_role_user_unit where role_rel_type = 'job' and role_rel_id = ? and sys_org_relation_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, oldJobHID);
            statement.setString(2, orgId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String roleUnitId = resultSet.getString("id");
                roleUnitIds.add(roleUnitId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleUnitIds;
    }

    private static void updateRoleUserUnit(String roleUnitId, String newJobHID) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "update sys_role_user_unit set role_rel_id = ? where id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, newJobHID);
            statement.setString(2, roleUnitId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```


请确保在运行代码之前，已经将数据库的JDBC连接URL、用户名和密码替换为实际的值，并根据实际情况修改departmentOID、oldJobHID和newJobHID的值。


希望这次的代码逻辑符合你的要求！


## 来自你的消息:


刚才的代码逻辑有错，请重新根据边边的逻辑重新输出对应的代码


请根据以下excel格式数据


职位名称 职位HID 旧岗位HID 新岗位HID 部门HID 部门OID
总监 854855155c77426fb3713cd66bb5820d 97424c621fc947078a3fdee7437bcf2e 24dfb4d4b55e4f8995d8179650c3da3b 480a9d926210406594386ca610a37ec4 f9e7e8416372411f912c5ef659829d32


编写程序如下


根据每一行部门OID查询对具体sql的org_id字段为查询条件如下：


select id from sys_org_relation sor where org_id = ‘f9e7e8416372411f912c5ef659829d32’;


根据上述的id，进行如下sql的递归查询：


select id,parent_id from sys_org_relation sor where id = ‘id’;


如果上述sql的parent_id不为-1，则继续递归查询，将所有的id存起来，直到parent_id为-1，递归结束。


将所有的id存起来，然后根据id查询对应的sys_org_relation_id，而role_rel_id则对应的是旧岗位HID，具体sql如下：


select id as role_unit_id from sys_role_user_unit where role_rel_type = ‘job’ and role_rel_id = ‘cb9d91767c7b46f7880eb96527cffee8’ and sys_org_relation_id = ‘上述查询的id’;


如果上述的sql查询到对应的数据，则生成对应的sql，role_rel_id为新岗位的id, id位上述的：role_unit_id：


update sys_role_user_unit set role_rel_id = ‘24dfb4d4b55e4f8995d8179650c3da3b’ where id = ‘role_unit_id’;


要求代码清晰，用java实现


## 来自 AIGC 的消息:


根据你提供的逻辑，以下是重新编写的代码：


```
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelDataProcessor {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/database_name";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        String departmentOID = "f9e7e8416372411f912c5ef659829d32";
        String oldJobHID = "97424c621fc947078a3fdee7437bcf2e";
        String newJobHID = "24dfb4d4b55e4f8995d8179650c3da3b";
        String orgId = getOrgId(departmentOID);
        if (orgId != null) {
            List<String> orgIds = getRecursiveOrgIds(orgId);
            for (String id : orgIds) {
                List<String> roleUnitIds = getRoleUnitIds(id, oldJobHID);
                for (String roleUnitId : roleUnitIds) {
                    updateRoleUserUnit(roleUnitId, newJobHID);
                }
            }
        }
    }

    private static String getOrgId(String departmentOID) {
        String orgId = null;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id from sys_org_relation where org_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, departmentOID);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                orgId = resultSet.getString("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orgId;
    }

    private static List<String> getRecursiveOrgIds(String orgId) {
        List<String> orgIds = new ArrayList<>();
        getRecursiveOrgIdsHelper(orgId, orgIds);
        return orgIds;
    }

    private static void getRecursiveOrgIdsHelper(String orgId, List<String> orgIds) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id, parent_id from sys_org_relation where id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, orgId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                String parentId = resultSet.getString("parent_id");
                orgIds.add(orgId);
                if (!parentId.equals("-1")) {
                    getRecursiveOrgIdsHelper(parentId, orgIds);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static List<String> getRoleUnitIds(String orgId, String oldJobHID) {
        List<String> roleUnitIds = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "select id from sys_role_user_unit where role_rel_type = 'job' and role_rel_id = ? and sys_org_relation_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, oldJobHID);
            statement.setString(2, orgId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String roleUnitId = resultSet.getString("id");
                roleUnitIds.add(roleUnitId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleUnitIds;
    }

    private static void updateRoleUserUnit(String roleUnitId, String newJobHID) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String sql = "update sys_role_user_unit set role_rel_id = ? where id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, newJobHID);
            statement.setString(2, roleUnitId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```


请确保在运行代码之前，已经将数据库的JDBC连接URL、用户名和密码替换为实际的值，并根据实际情况修改departmentOID、oldJobHID和newJobHID的值。


希望这次的代码逻辑符合你的要求！
