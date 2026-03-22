import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const SafetyScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Safety Dashboard</Text>
      <Text>Safety metrics and alerts here.</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24 },
});

export default SafetyScreen;