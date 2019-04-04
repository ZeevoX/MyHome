#pragma once

#include <stdbool.h>

#define LIGHT_SID 50

#define LIGHTS_SUBID 0
#define HEATER_SUBID 1

#define UPTIME_SUBID 0
#define HEAP_FREE_SUBID 1

#define TEMP_SUBID 0
#define RH_SUBID 1

struct sensor_data {
  int sid;
  int subid;
  double ts;
  double value;
  char *name;
};

void report_to_server(int sid, int subid, double ts, double value);
void report_to_server_sd(const struct sensor_data *sd);
void hub_add_data(const struct sensor_data *sd);
bool hub_get_data(int sid, int subid, struct sensor_data *sd);

bool hub_data_init(void);