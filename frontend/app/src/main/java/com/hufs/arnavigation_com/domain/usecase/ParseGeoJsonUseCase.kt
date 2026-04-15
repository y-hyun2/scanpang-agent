package com.scanpang.arnavigation.domain.usecase

import com.scanpang.arnavigation.data.remote.dto.TmapRouteResponse

class ParseGeoJsonUseCase {

    operator fun invoke(routeData: Any?): List<Pair<Double, Double>> {
        val response = routeData as? TmapRouteResponse ?: return emptyList()
        val routeNodes = mutableListOf<Pair<Double, Double>>()

        response.features.forEach { feature ->
            val geometry = feature.geometry

            when (geometry.type) {
                "LineString" -> {
                    val coordsList = geometry.coordinates as? List<*>
                    coordsList?.forEach { coord ->
                        val point = coord as? List<*>
                        if (point != null && point.size >= 2) {
                            val lng = (point[0] as? Number)?.toDouble() ?: return@forEach
                            val lat = (point[1] as? Number)?.toDouble() ?: return@forEach
                            routeNodes.add(Pair(lat, lng))
                        }
                    }
                }
                "Point" -> {
                    val coord = geometry.coordinates as? List<*>
                    if (coord != null && coord.size >= 2) {
                        val lng = (coord[0] as? Number)?.toDouble() ?: return@forEach
                        val lat = (coord[1] as? Number)?.toDouble() ?: return@forEach
                        routeNodes.add(Pair(lat, lng))
                    }
                }
            }
        }

        return routeNodes.distinct()
    }
}