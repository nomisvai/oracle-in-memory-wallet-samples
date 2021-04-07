package nomisvai.db;

import java.util.List;
import nomisvai.api.User;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserDao {
    @SqlUpdate("INSERT INTO users(id, name) VALUES (:id, :name)")
    void insert(@BindBean User user);

    @SqlQuery("SELECT * FROM users ORDER BY name")
    @RegisterBeanMapper(User.class)
    List<User> listUsers();

    @SqlQuery("select id, name from users where id = :id")
    @RegisterBeanMapper(User.class)
    User findById(@Bind("id") String id);
}
