package nomisvai.resources;

import java.util.List;
import java.util.UUID;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import nomisvai.api.User;
import nomisvai.db.UserDao;
import org.jdbi.v3.core.Jdbi;

@Path("/v1")
@Produces({"application/json"})
public class UserResource {
    private final UserDao userDao;

    public UserResource(Jdbi jdbi) {
        userDao = jdbi.onDemand(UserDao.class);

        // seed some users if none are present
        if (userDao.listUsers().size() == 0) {
            for (int i = 0; i < 10; i++) {
                userDao.insert(
                        User.builder().id(UUID.randomUUID().toString()).name("User" + i).build());
            }
        }
    }

    @GET
    @Path("/users")
    @Produces({"application/json"})
    public List<User> listUsers() {
        return userDao.listUsers();
    }

    @POST
    @Path("/users")
    @Produces({"application/json"})
    public User createUser(User user) {
        userDao.insert(user);
        return userDao.findById(user.getId());
    }
}
