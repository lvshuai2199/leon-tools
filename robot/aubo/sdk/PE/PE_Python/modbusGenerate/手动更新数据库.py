# INSERT INTO "main"."modbus_io"("io_name", "modbus_name", "addr_type", "addr") VALUES ('m121231', 'ab', 'DigitalInput', '3E8');
# INSERT INTO "main"."modbus_io"("io_name", "modbus_name", "addr_type", "addr") VALUES ('O2', 'ab', 'DigitalOutput', '44D');

str1 = ""
for i in range(1,6):
    # print(i)
    # hexadecimal = hex(i)
    # print(hexadecimal)
    hexadecimal = hex(i)[2:]
    print(hexadecimal.upper())
    str1 += "INSERT INTO main.modbus_io('io_name', 'modbus_name', 'addr_type', 'addr') VALUES ('m"+ str(i) + "', 'ab', 'DigitalInput','" + hexadecimal.upper() + "');" + '\n'
for i in range(10,16):
    # print(i)
    # hexadecimal = hex(i)
    # print(hexadecimal)
    hexadecimal = hex(i)[2:]
    print(hexadecimal.upper())
    str1 += "INSERT INTO main.modbus_io('io_name', 'modbus_name', 'addr_type', 'addr') VALUES ('m"+ str(i) + "', 'ab', 'DigitalOutput','" + hexadecimal.upper() + "');" + '\n'


print(str1)