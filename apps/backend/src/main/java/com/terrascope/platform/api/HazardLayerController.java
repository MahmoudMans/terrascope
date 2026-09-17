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
                new HazardLayerInfo(
                        HazardType.EARTHQUAKE.key(),
                        HazardType.EARTHQUAKE.label(),
                        "USGS",
                        "available",
                        true,
                        true
                ),
                new HazardLayerInfo(
                        HazardType.VOLCANO.key(),
                        HazardType.VOLCANO.label(),
                        "NASA EONET",
                        "available",
                        true,
                        true
                ),
                new HazardLayerInfo(
                        HazardType.WILDFIRE.key(),
                        HazardType.WILDFIRE.label(),
                        "NASA EONET",
                        "planned",
                        false,
                        false
                ),
                new HazardLayerInfo(
                        HazardType.SEVERE_STORM.key(),
                        HazardType.SEVERE_STORM.label(),
                        "NASA EONET",
                        "planned",
                        false,
                        false
                )
        );
    }
}
