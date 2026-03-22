import React from 'react';
import { View, Text, Button, StyleSheet } from 'react-native';

const IndexScreen: React.FC = ({ navigation }: any) => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>ARGODREIGN Dashboard</Text>
      <Button title="Machine Dashboard" onPress={() => navigation.navigate('Machine')} />
      <Button title="Safety Dashboard" onPress={() => navigation.navigate('Safety')} />
      <Button title="Plant Dashboard" onPress={() => navigation.navigate('Plant')} />
      <Button title="Compliance Dashboard" onPress={() => navigation.navigate('Compliance')} />
      <Button title="Handover Dashboard" onPress={() => navigation.navigate('Handover')} />
      <Button title="Timeline Dashboard" onPress={() => navigation.navigate('Timeline')} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 20 },
  title: { fontSize: 24, marginBottom: 20 },
});

export default IndexScreen;