package com.terrascope.platform.api;

import com.terrascope.platform.api.model.HazardLayerInfo;
import com.terrascope.platform.domain.HazardType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hazards")
public class HazardLayerController {

    @GetMapping("/layers")
    public List<HazardLayerInfo> layers() {
        return List.of(
                layer(
                        HazardType.EARTHQUAKE,
                        "USGS"
                ),
                layer(
                        HazardType.VOLCANO,
                        "NASA EONET"
                ),
                layer(
                        HazardType.WILDFIRE,
                        "NASA EONET"
                ),
                layer(
                        HazardType.SEVERE_STORM,
                        "NASA EONET"
                )
        );
    }

    private HazardLayerInfo layer(
            HazardType hazardType,
            String source
    ) {
        return new HazardLayerInfo(
                hazardType.key(),
                hazardType.label(),
                source,
                "available",
                true,
                true
        );
    }
}
