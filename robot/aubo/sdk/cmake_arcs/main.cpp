#include <iostream>

using namespace std;
#include "robotcontrol.h"
int main()
{
    cout << "Hello World!" << endl;

    robotControl *robot = new robotControl();

    robot->example_powerOnAndOff();

    return 0;
}
