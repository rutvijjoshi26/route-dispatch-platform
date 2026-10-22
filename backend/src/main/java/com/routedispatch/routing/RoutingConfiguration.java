package com.routedispatch.routing;

import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class RoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RoutingConfiguration.class);

    @Bean
    TravelTimeProvider travelTimeProvider(RoutingProperties properties, RestClient.Builder builder) {
        TravelTimeProvider provider = switch (properties.provider()) {
            case OSRM -> {
                var httpClient = HttpClient.newBuilder().connectTimeout(properties.osrm().timeout()).build();
                var requestFactory = new JdkClientHttpRequestFactory(httpClient);
                requestFactory.setReadTimeout(properties.osrm().timeout());
                RestClient client = builder.clone()
                        .baseUrl(properties.osrm().baseUrl())
                        .requestFactory(requestFactory)
                        .build();
                yield new OsrmTravelTimeProvider(client, properties.osrm().tableChunkSize());
            }
            case HAVERSINE -> new HaversineTravelTimeProvider(
                    properties.haversine().circuityFactor(), properties.haversine().averageSpeedKph());
        };
        log.info("Routing provider: {}", provider.name());
        return provider;
    }

    @Bean
    TravelMatrixService travelMatrixService(TravelTimeProvider provider, RoutingProperties properties) {
        return new TravelMatrixService(provider, properties.cacheMaxPairs());
    }
}
