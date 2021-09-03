import * as React from 'react';

import { StyleSheet, View,  } from 'react-native';
import ScannerViewManager from 'react-native-scanner';

export default function App() {
  return (
    <View style={styles.container}>
      {/* <View style={{ height:56, width: '100%'}}/> */}
      <ScannerViewManager 
        color="#32a852" style={styles.box} 
        scanBarcode
        showFrame
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    flex: 1,
    width: '100%',
    height: '100%'
  },
});
