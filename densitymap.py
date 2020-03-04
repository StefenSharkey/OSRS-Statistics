import mysql.connector as mariadb
from mysql.connector import errorcode

import matplotlib.pyplot as plt
import matplotlib.cm as cm
from matplotlib.colors import LogNorm
import numpy as np

try:
    connection = mariadb.connect(user='xpstatistics', host='67.246.243.177', database='xp_statistics')
except mariadb.Error as err:
  if err.errno == errorcode.ER_ACCESS_DENIED_ERROR:
    print("Incorrect credentials.")
  elif err.errno == errorcode.ER_BAD_DB_ERROR:
    print("Database does not exist.")
  else:
    print(err)
else:
  print("Connected.")

  cursor = connection.cursor()
  cursor.execute("SELECT * FROM xp_statistics WHERE username = 'LordOfWoeHC'")

  result = cursor.fetchall()

  for x in result:
      print(x)
  
  connection.close()


#x, y, z = np.loadtxt('data.txt', unpack=True)
#N = int(len(z)**.5)
#z = z.reshape(N, N)
#plt.imshow(z+10, extent=(np.amin(x), np.amax(x), np.amin(y), np.amax(y)),
#        cmap=cm.hot, norm=LogNorm())
#plt.colorbar()
#plt.show()
