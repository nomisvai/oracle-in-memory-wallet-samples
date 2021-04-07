package nomisvai.configuration;

import io.dropwizard.Configuration;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SampleServiceConfiguration extends Configuration {
    @NotNull InMemoryWalletDataSourceFactory database;
    @NotNull private Stage stage;

    public enum Stage {
        local,
        cloud
    }
}
