#include <iostream>

using namespace std;

#include "robotcontrol.h"

int main()
{
    cout << "Hello World!" << endl;

    robotControl *robotCon = new robotControl();

    robotCon->robotStartUp();
    return 0;
}
